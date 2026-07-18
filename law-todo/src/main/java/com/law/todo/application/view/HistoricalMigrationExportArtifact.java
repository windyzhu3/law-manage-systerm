package com.law.todo.application.view;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

public record HistoricalMigrationExportArtifact(InputStream input,long sizeBytes,long rowCount,
        String csvSha256,Instant generatedAt) implements AutoCloseable
{
    @Override public void close() throws IOException { input.close(); }
}
