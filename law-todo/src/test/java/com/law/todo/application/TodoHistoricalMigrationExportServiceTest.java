package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.cursor.Cursor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.law.todo.application.view.HistoricalMigrationCaseCandidate;
import com.law.todo.application.view.HistoricalMigrationExportArtifact;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoHistoricalMigrationReadinessMapper;

class TodoHistoricalMigrationExportServiceTest
{
    private final TodoHistoricalMigrationReadinessMapper mapper=mock(TodoHistoricalMigrationReadinessMapper.class);
    private final HistoricalMigrationExportArchiveWriter writer=mock(HistoricalMigrationExportArchiveWriter.class);
    private final TodoHistoricalMigrationExportService service=new TodoHistoricalMigrationExportService(mapper,writer);

    @Test void streamsAllEightSourceFieldsAndClosesCursorAfterSuccessfulArchive() throws Exception
    {
        Cursor<Map<String,Object>> cursor=cursor(List.of(
                row(1L,"CASE-001","Alpha","CIVIL","OPEN",11L,21L,31L),
                row("2","CASE-002",null,"CRIMINAL","CLOSED",null,"22","32")));
        HistoricalMigrationExportArtifact artifact=artifact(2,new TrackingInputStream());
        when(mapper.selectHistoricalMigrationPreflightCounts()).thenReturn(Map.of("active_case_count",2));
        when(mapper.streamHistoricalCaseCandidates()).thenReturn(cursor);
        when(writer.write(any())).thenReturn(artifact);

        HistoricalMigrationExportArtifact result=service.export("G-04");

        assertSame(artifact,result);
        ArgumentCaptor<Iterator<HistoricalMigrationCaseCandidate>> candidates=ArgumentCaptor.forClass(Iterator.class);
        verify(writer).write(candidates.capture());
        List<HistoricalMigrationCaseCandidate> values=new ArrayList<>();
        candidates.getValue().forEachRemaining(values::add);
        assertEquals(List.of(
                new HistoricalMigrationCaseCandidate(1L,"CASE-001","Alpha","CIVIL","OPEN",11L,21L,31L),
                new HistoricalMigrationCaseCandidate(2L,"CASE-002","","CRIMINAL","CLOSED",null,22L,32L)),values);
        verify(cursor).close();
    }

    @Test void closesCursorWhenArchiveWriterFails() throws Exception
    {
        Cursor<Map<String,Object>> cursor=cursor(List.of(row(1L,"CASE-001","Alpha","CIVIL","OPEN",null,null,null)));
        when(mapper.selectHistoricalMigrationPreflightCounts()).thenReturn(Map.of("active_case_count",1));
        when(mapper.streamHistoricalCaseCandidates()).thenReturn(cursor);
        when(writer.write(any())).thenThrow(new IllegalStateException("disk unavailable"));

        TodoException error=assertThrows(TodoException.class,()->service.export("G-04"));

        assertEquals("TODO_MIGRATION_EXPORT_FAILED",error.getBusinessCode());
        assertEquals("Historical migration export could not be generated",error.getMessage());
        verify(cursor).close();
    }

    @Test void closesMismatchedArtifactAndCursor() throws Exception
    {
        Cursor<Map<String,Object>> cursor=cursor(List.of(row(1L,"CASE-001","Alpha","CIVIL","OPEN",null,null,null)));
        TrackingInputStream input=new TrackingInputStream();
        HistoricalMigrationExportArtifact artifact=artifact(1,input);
        when(mapper.selectHistoricalMigrationPreflightCounts()).thenReturn(Map.of("active_case_count",2));
        when(mapper.streamHistoricalCaseCandidates()).thenReturn(cursor);
        when(writer.write(any())).thenReturn(artifact);

        TodoException error=assertThrows(TodoException.class,()->service.export("G-04"));

        assertEquals("TODO_MIGRATION_EXPORT_FAILED",error.getBusinessCode());
        assertTrue(input.closed);
        verify(cursor).close();
    }

    @Test void rejectsEveryGateExceptG04BeforeReadingAnything()
    {
        TodoException error=assertThrows(TodoException.class,()->service.export("G-05"));

        assertEquals("TODO_MIGRATION_GATE_UNSUPPORTED",error.getBusinessCode());
        verifyNoInteractions(mapper,writer);
    }

    @Test void exportUsesReadOnlyRepeatableReadTransaction() throws Exception
    {
        Transactional transaction=TodoHistoricalMigrationExportService.class
                .getDeclaredMethod("export",String.class).getAnnotation(Transactional.class);

        assertTrue(transaction.readOnly());
        assertEquals(Isolation.REPEATABLE_READ,transaction.isolation());
    }

    @SuppressWarnings("unchecked")
    private static Cursor<Map<String,Object>> cursor(List<Map<String,Object>> rows)
    {
        Cursor<Map<String,Object>> cursor=mock(Cursor.class);
        when(cursor.iterator()).thenReturn(rows.iterator());
        return cursor;
    }

    private static Map<String,Object> row(Object caseId,Object caseNo,Object caseName,Object caseType,
            Object caseStatus,Object contractId,Object mainLawyerId,Object deptId)
    {
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("case_id",caseId);row.put("case_no",caseNo);row.put("case_name",caseName);
        row.put("case_type",caseType);row.put("case_status",caseStatus);row.put("contract_id",contractId);
        row.put("main_lawyer_id",mainLawyerId);row.put("dept_id",deptId);
        return row;
    }

    private static HistoricalMigrationExportArtifact artifact(long rows,TrackingInputStream input)
    {return new HistoricalMigrationExportArtifact(input,12,rows,"sha",Instant.parse("2026-07-18T00:00:00Z"));}

    private static final class TrackingInputStream extends ByteArrayInputStream
    {
        private boolean closed;
        private TrackingInputStream(){super(new byte[]{1,2,3});}
        @Override public void close() throws IOException {closed=true;super.close();}
    }
}
