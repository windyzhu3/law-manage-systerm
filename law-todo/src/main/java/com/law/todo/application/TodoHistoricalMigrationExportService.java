package com.law.todo.application;

import java.util.Iterator;
import java.util.Map;

import org.apache.ibatis.cursor.Cursor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.law.todo.application.view.HistoricalMigrationCaseCandidate;
import com.law.todo.application.view.HistoricalMigrationExportArtifact;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoHistoricalMigrationReadinessMapper;

@Service
public class TodoHistoricalMigrationExportService
{
    private final TodoHistoricalMigrationReadinessMapper mapper;
    private final HistoricalMigrationExportArchiveWriter writer;

    public TodoHistoricalMigrationExportService(TodoHistoricalMigrationReadinessMapper mapper,
            HistoricalMigrationExportArchiveWriter writer)
    {this.mapper=mapper;this.writer=writer;}

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public HistoricalMigrationExportArtifact export(String gateCode)
    {
        requireGate(gateCode);
        long expected=number(mapper.selectHistoricalMigrationPreflightCounts(),"active_case_count");
        try(Cursor<Map<String,Object>> cursor=mapper.streamHistoricalCaseCandidates()) {
            Iterator<Map<String,Object>> rows=cursor.iterator();
            Iterator<HistoricalMigrationCaseCandidate> candidates=new Iterator<>() {
                @Override public boolean hasNext(){return rows.hasNext();}
                @Override public HistoricalMigrationCaseCandidate next(){return candidate(rows.next());}
            };
            HistoricalMigrationExportArtifact artifact=writer.write(candidates);
            if(artifact.rowCount()!=expected) {
                artifact.close();
                throw exportFailed();
            }
            return artifact;
        } catch(TodoException error) {
            throw error;
        } catch(Exception error) {
            throw exportFailed();
        }
    }

    private static HistoricalMigrationCaseCandidate candidate(Map<String,Object> row)
    {
        return new HistoricalMigrationCaseCandidate(nullableLong(row,"case_id"),text(row,"case_no"),
                text(row,"case_name"),text(row,"case_type"),text(row,"case_status"),
                nullableLong(row,"contract_id"),nullableLong(row,"main_lawyer_id"),nullableLong(row,"dept_id"));
    }

    private static Long nullableLong(Map<String,Object> row,String key)
    {
        Object value=row.get(key);
        if(value==null)return null;
        return value instanceof Number number?number.longValue():Long.valueOf(String.valueOf(value));
    }

    private static String text(Map<String,Object> row,String key)
    {
        Object value=row.get(key);
        return value==null?"":String.valueOf(value);
    }

    private static long number(Map<String,Object> row,String key)
    {
        Object value=row.get(key);
        return value instanceof Number number?number.longValue():Long.parseLong(String.valueOf(value));
    }

    private static void requireGate(String value)
    {
        if(!"G-04".equals(value))
            throw new TodoException("TODO_MIGRATION_GATE_UNSUPPORTED","Only G-04 is supported");
    }

    private static TodoException exportFailed()
    {return new TodoException("TODO_MIGRATION_EXPORT_FAILED","Historical migration export could not be generated");}
}
