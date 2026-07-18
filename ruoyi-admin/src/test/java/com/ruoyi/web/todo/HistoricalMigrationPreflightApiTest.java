package com.ruoyi.web.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.law.todo.application.TodoHistoricalMigrationExportService;
import com.law.todo.application.TodoHistoricalMigrationPreflightService;
import com.law.todo.application.TodoHistoricalMigrationReadinessService;
import com.law.todo.application.view.HistoricalMigrationExportArtifact;
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
        HistoricalMigrationExportArtifact artifact=mock(HistoricalMigrationExportArtifact.class);
        when(artifact.input()).thenReturn(new ByteArrayInputStream(new byte[]{1,2,3}));
        when(artifact.sizeBytes()).thenReturn(3L);
        when(artifact.rowCount()).thenReturn(2L);
        when(artifact.csvSha256()).thenReturn(SHA);
        when(exports.export("G-04")).thenReturn(artifact);
        TodoHistoricalMigrationReadinessController controller=controller(exports);

        Method method=TodoHistoricalMigrationReadinessController.class.getDeclaredMethod("exceptionExport",String.class);
        assertPermission(method,"todo:admission:export");
        assertGetMapping(method,"/exception-export");

        ResponseEntity<StreamingResponseBody> response=controller.exceptionExport("G-04");
        assertEquals("application/zip",response.getHeaders().getContentType().toString());
        assertEquals("attachment; filename=\"g04-historical-case-preflight.zip\"",
                response.getHeaders().getFirst("Content-Disposition"));
        assertEquals("2",response.getHeaders().getFirst("X-Exception-Row-Count"));
        assertEquals(SHA,response.getHeaders().getFirst("X-Exception-CSV-SHA256"));
        assertEquals("no-store",response.getHeaders().getCacheControl());
        assertEquals("nosniff",response.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals(3L,response.getHeaders().getContentLength());

        ByteArrayOutputStream output=new ByteArrayOutputStream();
        response.getBody().writeTo(output);
        assertEquals(3,output.size());
        verify(artifact).close();
    }

    @Test void closesArtifactWhenClientDisconnects() throws Exception
    {
        TodoHistoricalMigrationExportService exports=mock(TodoHistoricalMigrationExportService.class);
        HistoricalMigrationExportArtifact artifact=mock(HistoricalMigrationExportArtifact.class);
        when(artifact.input()).thenReturn(new ByteArrayInputStream(new byte[]{1,2,3}));
        when(exports.export("G-04")).thenReturn(artifact);
        ResponseEntity<StreamingResponseBody> response=controller(exports).exceptionExport("G-04");
        OutputStream disconnected=new OutputStream() {
            @Override public void write(int value) throws IOException {throw new IOException("client disconnected");}
        };

        assertThrows(IOException.class,()->response.getBody().writeTo(disconnected));
        verify(artifact).close();
    }

    private static TodoHistoricalMigrationReadinessController controller(TodoHistoricalMigrationExportService exports)
    {
        return new TodoHistoricalMigrationReadinessController(mock(TodoHistoricalMigrationReadinessService.class),
                mock(TodoHistoricalMigrationPreflightService.class),exports);
    }

    private static void assertPermission(Method method,String permission)
    {assertEquals("@ss.hasPermi('"+permission+"')",method.getAnnotation(PreAuthorize.class).value());}

    private static void assertGetMapping(Method method,String path)
    {assertEquals(path,method.getAnnotation(GetMapping.class).value()[0]);}
}
