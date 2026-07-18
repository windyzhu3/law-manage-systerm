package com.ruoyi.web.audit;

public interface HistoricalMigrationExportAudit
{
    Transfer begin(long rowCount);

    interface Transfer
    {
        void success();
        void failure();
    }
}
