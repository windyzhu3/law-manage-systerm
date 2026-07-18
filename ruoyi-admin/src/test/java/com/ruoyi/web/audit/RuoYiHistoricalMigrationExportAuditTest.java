package com.ruoyi.web.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ruoyi.common.enums.BusinessStatus;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.enums.OperatorType;
import com.ruoyi.system.domain.SysOperLog;
import com.ruoyi.system.service.ISysOperLogService;

class RuoYiHistoricalMigrationExportAuditTest
{
    @Test void writesCompleteSanitizedSuccessOnlyWhenTransferCompletes()
    {
        ISysOperLogService logs=mock(ISysOperLogService.class);
        AtomicLong milliseconds=new AtomicLong(1_000L);
        RuoYiHistoricalMigrationExportAudit audit=new RuoYiHistoricalMigrationExportAudit(logs,
                ()->metadata(),milliseconds::get);

        HistoricalMigrationExportAudit.Transfer transfer=audit.begin(7L);
        milliseconds.set(1_025L);
        transfer.success();

        SysOperLog log=captured(logs);
        assertEquals("G-04历史迁移异常清单",log.getTitle());
        assertEquals(BusinessType.EXPORT.ordinal(),log.getBusinessType());
        assertEquals(OperatorType.MANAGE.ordinal(),log.getOperatorType());
        assertEquals(BusinessStatus.SUCCESS.ordinal(),log.getStatus());
        assertEquals("reviewer",log.getOperName());
        assertEquals("案管部",log.getDeptName());
        assertEquals("203.0.113.9",log.getOperIp());
        assertEquals("/todo/foundation-migration/exception-export",log.getOperUrl());
        assertEquals("GET",log.getRequestMethod());
        assertEquals("TodoHistoricalMigrationReadinessController.exceptionExport()",log.getMethod());
        assertEquals("rowCount=7",log.getOperParam());
        assertEquals("outcome=SUCCESS,rowCount=7",log.getJsonResult());
        assertEquals(25L,log.getCostTime());
        assertEquals(null,log.getErrorMsg());
        assertSensitiveExportDataAbsent(log);
    }

    @Test void writesSanitizedFailureOutcomeWithoutTheTransferException()
    {
        ISysOperLogService logs=mock(ISysOperLogService.class);
        AtomicLong milliseconds=new AtomicLong(2_000L);
        RuoYiHistoricalMigrationExportAudit audit=new RuoYiHistoricalMigrationExportAudit(logs,
                ()->metadata(),milliseconds::get);

        HistoricalMigrationExportAudit.Transfer transfer=audit.begin(9L);
        milliseconds.set(2_010L);
        transfer.failure();

        SysOperLog log=captured(logs);
        assertEquals(BusinessStatus.FAIL.ordinal(),log.getStatus());
        assertEquals("outcome=FAILURE,rowCount=9",log.getJsonResult());
        assertEquals("Historical migration export transfer failed",log.getErrorMsg());
        assertEquals(10L,log.getCostTime());
        assertSensitiveExportDataAbsent(log);
    }

    private static RuoYiHistoricalMigrationExportAudit.Metadata metadata()
    {
        return new RuoYiHistoricalMigrationExportAudit.Metadata("reviewer","案管部","203.0.113.9",
                "/todo/foundation-migration/exception-export","GET");
    }

    private static SysOperLog captured(ISysOperLogService logs)
    {
        ArgumentCaptor<SysOperLog> captured=ArgumentCaptor.forClass(SysOperLog.class);
        verify(logs).insertOperlog(captured.capture());
        return captured.getValue();
    }

    private static void assertSensitiveExportDataAbsent(SysOperLog log)
    {
        String recorded=String.join("|",String.valueOf(log.getTitle()),String.valueOf(log.getOperParam()),
                String.valueOf(log.getJsonResult()),String.valueOf(log.getErrorMsg()));
        for(String forbidden:new String[]{"case name","historical-case-exceptions.csv","sensitive body",
                "C:\\temp\\archive.zip","0123456789abcdef","csvSha256"})
            assertFalse(recorded.contains(forbidden),"Audit must not contain "+forbidden);
    }
}
