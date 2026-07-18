package com.ruoyi.web.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.law.todo.application.TodoHistoricalMigrationExportService;
import com.law.todo.application.TodoHistoricalMigrationPreflightService;
import com.law.todo.application.TodoHistoricalMigrationReadinessService;
import com.law.todo.application.view.HistoricalMigrationExportArtifact;
import com.law.todo.domain.TodoException;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.web.audit.HistoricalMigrationExportAudit;
import com.ruoyi.web.controller.todo.TodoHistoricalMigrationReadinessController;

class HistoricalMigrationPreflightApiTest
{
    private static final String SHA="a".repeat(64);

    @Test void exposes_g04_preflight_with_admission_view_permission() throws Exception
    {
        Method method=TodoHistoricalMigrationReadinessController.class.getDeclaredMethod("preflight",String.class);

        assertEquals("/preflight",method.getAnnotation(GetMapping.class).value()[0]);
        assertTrue(method.getAnnotation(PreAuthorize.class).value().contains("todo:admission:view"));
    }

    @Test void exposesControlledZipResponseWithDedicatedPermission() throws Exception
    {
        TodoHistoricalMigrationExportService exports=mock(TodoHistoricalMigrationExportService.class);
        TrackingInputStream input=new TrackingInputStream(new byte[]{1,2,3});
        HistoricalMigrationExportArtifact artifact=artifact(input,3L,2L);
        when(exports.export("G-04")).thenReturn(artifact);
        RecordingAudit audit=new RecordingAudit();
        TodoHistoricalMigrationReadinessController controller=controller(exports,audit);

        Method method=TodoHistoricalMigrationReadinessController.class.getDeclaredMethod("exceptionExport",String.class);
        assertPermission(method,"todo:admission:export");
        assertGetMapping(method,"/exception-export");
        assertNull(method.getAnnotation(Log.class),"Deferred exports must not use premature @AfterReturning audit");

        ResponseEntity<StreamingResponseBody> response=controller.exceptionExport("G-04");
        assertEquals("application/zip",response.getHeaders().getContentType().toString());
        assertEquals("attachment; filename=\"g04-historical-case-preflight.zip\"",
                response.getHeaders().getFirst("Content-Disposition"));
        assertEquals("2",response.getHeaders().getFirst("X-Exception-Row-Count"));
        assertEquals(SHA,response.getHeaders().getFirst("X-Exception-CSV-SHA256"));
        assertEquals("no-store",response.getHeaders().getCacheControl());
        assertEquals("nosniff",response.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals(3L,response.getHeaders().getContentLength());
        assertEquals(2L,audit.rowCount);
        assertNull(audit.outcome,"No success audit may be recorded before the deferred body runs");

        ByteArrayOutputStream output=new ByteArrayOutputStream() {
            @Override public synchronized void write(byte[] bytes,int offset,int length)
            {
                assertNull(audit.outcome,"Success must wait until the complete copy finishes");
                super.write(bytes,offset,length);
            }
        };
        response.getBody().writeTo(output);
        assertEquals(3,output.size());
        assertEquals("SUCCESS",audit.outcome);
        assertEquals(1,audit.successRecords);
        assertEquals(0,audit.failureRecords);
        assertTrue(input.closed);
    }

    @Test void closesArtifactWhenClientDisconnects() throws Exception
    {
        TodoHistoricalMigrationExportService exports=mock(TodoHistoricalMigrationExportService.class);
        TrackingInputStream input=new TrackingInputStream(new byte[]{1,2,3});
        HistoricalMigrationExportArtifact artifact=artifact(input,3L,2L);
        when(exports.export("G-04")).thenReturn(artifact);
        RecordingAudit audit=new RecordingAudit();
        ResponseEntity<StreamingResponseBody> response=controller(exports,audit).exceptionExport("G-04");
        OutputStream disconnected=new OutputStream() {
            @Override public void write(int value) throws IOException {throw new IOException("client disconnected");}
        };

        assertThrows(IOException.class,()->response.getBody().writeTo(disconnected));
        assertEquals("FAILURE",audit.outcome);
        assertEquals(0,audit.successRecords);
        assertEquals(1,audit.failureRecords);
        assertTrue(input.closed);
    }

    @Test void auditFailureNeverMasksSuccessfulOrFailedTransfer() throws Exception
    {
        TodoHistoricalMigrationExportService successfulExports=mock(TodoHistoricalMigrationExportService.class);
        when(successfulExports.export("G-04")).thenReturn(artifact(new TrackingInputStream(new byte[]{1,2,3}),3L,2L));
        ResponseEntity<StreamingResponseBody> success=controller(successfulExports,new ThrowingAudit()).exceptionExport("G-04");
        ByteArrayOutputStream copied=new ByteArrayOutputStream();
        success.getBody().writeTo(copied);
        assertEquals(3,copied.size());

        TodoHistoricalMigrationExportService failedExports=mock(TodoHistoricalMigrationExportService.class);
        when(failedExports.export("G-04")).thenReturn(artifact(new TrackingInputStream(new byte[]{1}),1L,1L));
        ResponseEntity<StreamingResponseBody> failure=controller(failedExports,new ThrowingAudit()).exceptionExport("G-04");
        IOException disconnect=new IOException("client disconnected");
        IOException observed=assertThrows(IOException.class,()->failure.getBody().writeTo(new OutputStream() {
            @Override public void write(int value) throws IOException {throw disconnect;}
        }));
        assertSame(disconnect,observed);

        TodoHistoricalMigrationExportService beginFailureExports=mock(TodoHistoricalMigrationExportService.class);
        when(beginFailureExports.export("G-04"))
                .thenReturn(artifact(new TrackingInputStream(new byte[]{4,5}),2L,1L));
        ResponseEntity<StreamingResponseBody> beginFailure=
                controller(beginFailureExports,new ThrowingBeginAudit()).exceptionExport("G-04");
        ByteArrayOutputStream beginFailureOutput=new ByteArrayOutputStream();
        beginFailure.getBody().writeTo(beginFailureOutput);
        assertEquals(2,beginFailureOutput.size());
    }

    @Test void auditsArchiveGenerationFailureExactlyOnceAndPreservesTheStableException()
    {
        TodoException failure=new TodoException("TODO_MIGRATION_EXPORT_FAILED",
                "Historical migration export could not be generated");
        assertGenerationFailureIsAudited("G-04",failure);
    }

    @Test void auditsUnsupportedGateExactlyOnceAndPreservesTheStableException()
    {
        TodoException failure=new TodoException("TODO_MIGRATION_GATE_UNSUPPORTED","Only G-04 is supported");
        assertGenerationFailureIsAudited("G-05",failure);
    }

    @Test void auditFailureNeverMasksTheOriginalGenerationFailure()
    {
        assertAuditFailureDoesNotMaskGenerationFailure(new ThrowingAudit());
        assertAuditFailureDoesNotMaskGenerationFailure(new ThrowingBeginAudit());
    }

    private static void assertGenerationFailureIsAudited(String gateCode,TodoException failure)
    {
        TodoHistoricalMigrationExportService exports=mock(TodoHistoricalMigrationExportService.class);
        when(exports.export(gateCode)).thenThrow(failure);
        RecordingAudit audit=new RecordingAudit();

        TodoException observed=assertThrows(TodoException.class,
                ()->controller(exports,audit).exceptionExport(gateCode));

        assertSame(failure,observed,"Auditing must not replace the stable generation exception");
        assertEquals(1,audit.attempts,"Every export attempt must be captured before generation starts");
        assertEquals(0,audit.successRecords,"Generation failure must never record success");
        assertEquals(1,audit.failureRecords,"Generation failure must be recorded exactly once");
    }

    private static void assertAuditFailureDoesNotMaskGenerationFailure(HistoricalMigrationExportAudit audit)
    {
        TodoException failure=new TodoException("TODO_MIGRATION_EXPORT_FAILED",
                "Historical migration export could not be generated");
        TodoHistoricalMigrationExportService exports=mock(TodoHistoricalMigrationExportService.class);
        when(exports.export("G-04")).thenThrow(failure);

        TodoException observed=assertThrows(TodoException.class,
                ()->controller(exports,audit).exceptionExport("G-04"));

        assertSame(failure,observed);
    }

    private static HistoricalMigrationExportArtifact artifact(TrackingInputStream input,long size,long rows)
    {return new HistoricalMigrationExportArtifact(input,size,rows,SHA,Instant.EPOCH);}

    private static TodoHistoricalMigrationReadinessController controller(TodoHistoricalMigrationExportService exports,
            HistoricalMigrationExportAudit audit)
    {
        return new TodoHistoricalMigrationReadinessController(mock(TodoHistoricalMigrationReadinessService.class),
                mock(TodoHistoricalMigrationPreflightService.class),exports,audit);
    }

    private static void assertPermission(Method method,String permission)
    {assertEquals("@ss.hasPermi('"+permission+"')",method.getAnnotation(PreAuthorize.class).value());}

    private static void assertGetMapping(Method method,String path)
    {assertEquals(path,method.getAnnotation(GetMapping.class).value()[0]);}

    private static final class TrackingInputStream extends ByteArrayInputStream
    {
        private boolean closed;
        private TrackingInputStream(byte[] bytes){super(bytes);}
        @Override public void close() throws IOException {closed=true;super.close();}
    }

    private static final class RecordingAudit implements HistoricalMigrationExportAudit
    {
        private int attempts;
        private int successRecords;
        private int failureRecords;
        private long rowCount=-1;
        private String outcome;
        @Override public Attempt begin()
        {
            attempts++;
            return new Attempt() {
                @Override public void generated(long rows){rowCount=rows;}
                @Override public void success(){successRecords++;outcome="SUCCESS";}
                @Override public void failure(){failureRecords++;outcome="FAILURE";}
            };
        }
    }

    private static final class ThrowingAudit implements HistoricalMigrationExportAudit
    {
        @Override public Attempt begin()
        {
            return new Attempt() {
                @Override public void generated(long rows){throw new IllegalStateException("audit unavailable");}
                @Override public void success(){throw new IllegalStateException("audit unavailable");}
                @Override public void failure(){throw new IllegalStateException("audit unavailable");}
            };
        }
    }

    private static final class ThrowingBeginAudit implements HistoricalMigrationExportAudit
    {
        @Override public Attempt begin(){throw new IllegalStateException("audit metadata unavailable");}
    }
}
