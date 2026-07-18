package com.ruoyi.web.audit;

public interface HistoricalMigrationExportAudit
{
    Attempt begin();

    interface Attempt
    {
        void generated(long rowCount);
        void success();
        void failure();
    }
}
