package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.application.view.HistoricalMigrationCaseCandidate;
import com.law.todo.application.view.HistoricalMigrationExportArtifact;
import com.law.todo.domain.TodoException;

class HistoricalMigrationExportArchiveWriterTest
{
    private static final Instant GENERATED_AT=Instant.parse("2026-07-18T12:00:00Z");

    @TempDir Path temporaryDirectory;

    @Test void writesDeterministicEscapedCsvAndManifestThenRemovesFilesOnClose() throws Exception
    {
        HistoricalMigrationExportArchiveWriter writer=writer();
        List<HistoricalMigrationCaseCandidate> candidates=List.of(
                new HistoricalMigrationCaseCandidate(1L,"CASE-001","=HYPERLINK(\"x\")","CIVIL","OPEN",11L,21L,31L),
                new HistoricalMigrationCaseCandidate(2L,"CASE-002","quoted \"value\"\nand newline","CRIMINAL","CLOSED",12L,22L,32L));

        HistoricalMigrationExportArtifact artifact=writer.write(candidates.iterator());
        assertEquals(2,artifact.rowCount());
        assertEquals(GENERATED_AT,artifact.generatedAt());
        Map<String,byte[]> entries=zipEntries(artifact.input().readAllBytes());
        byte[] csvBytes=entries.get("historical-case-exceptions.csv");
        String csv=new String(csvBytes,StandardCharsets.UTF_8);
        JSONObject manifest=JSON.parseObject(entries.get("manifest.json"));
        assertEquals(List.of("historical-case-exceptions.csv","manifest.json"),new ArrayList<>(entries.keySet()));
        assertTrue(csv.startsWith("\ufeff\"case_id\",\"case_no\""));
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"x\"\")\""));
        assertTrue(csv.contains("\r\n"));
        assertFalse(csv.contains("NON_LITIGATION"));
        assertEquals(sha256(csvBytes),artifact.csvSha256());
        assertEquals(artifact.csvSha256(),manifest.getString("csvSha256"));
        assertEquals("UNREVIEWED",manifest.getString("classificationState"));
        assertEquals(List.of("NON_LITIGATION","COMPREHENSIVE","EXECUTION"),manifest.getList("allowedBusinessLines",String.class));

        HistoricalMigrationExportArtifact repeated=writer.write(candidates.iterator());
        assertEquals(artifact.csvSha256(),repeated.csvSha256());
        repeated.close();
        artifact.close();
        assertNoRegularFiles();
    }

    @Test void removesFilesAndHidesPathsWhenInputIteratorFails()
    {
        HistoricalMigrationExportArchiveWriter writer=writer();
        Iterator<HistoricalMigrationCaseCandidate> failing=new Iterator<>() {
            private int index;
            @Override public boolean hasNext(){return index<2;}
            @Override public HistoricalMigrationCaseCandidate next(){
                if(index++==0)return new HistoricalMigrationCaseCandidate(1L,"CASE-001","name","CIVIL","OPEN",null,null,null);
                throw new IllegalStateException("database cursor failed");
            }
        };

        TodoException exception=assertThrows(TodoException.class,()->writer.write(failing));
        assertEquals("TODO_MIGRATION_EXPORT_FAILED",exception.getBusinessCode());
        assertEquals("Historical migration export could not be generated",exception.getMessage());
        assertNoRegularFiles();
    }

    @Test void streamsOneHundredThousandRowsOnceAndDeletesBothTemporaryFilesOnClose() throws Exception
    {
        CountingCandidates candidates=new CountingCandidates(100_000);
        HistoricalMigrationExportArtifact artifact=writer().write(candidates);

        assertEquals(100_000,candidates.nextCalls);
        assertEquals(100_000,artifact.rowCount());
        assertTrue(artifact.sizeBytes()>0);
        Map<String,byte[]> entries=zipEntries(artifact.input().readAllBytes());
        assertNotNull(entries.get("historical-case-exceptions.csv"));
        assertNotNull(entries.get("manifest.json"));
        artifact.close();
        assertNoRegularFiles();
    }

    @Test void closeFailureStillAttemptsZipDeletionAndSuccessfulCloseIsIdempotent() throws Exception
    {
        ControlledCleanup cleanup=new ControlledCleanup(1,0);
        HistoricalMigrationExportArtifact artifact=writer(cleanup).write(List.of(candidate()).iterator());

        IOException failure=assertThrows(IOException.class,artifact::close);
        assertEquals("Historical migration export could not be cleaned up",failure.getMessage());
        assertEquals(1,cleanup.closeAttempts);
        assertEquals(1,cleanup.deleteAttempts);
        assertNoRegularFiles();

        artifact.close();
        artifact.close();
        assertEquals(1,cleanup.closeAttempts);
        assertEquals(1,cleanup.deleteAttempts);
    }

    @Test void closeCombinesFailuresWithoutPathsAndRetriesFailedDeletion() throws Exception
    {
        ControlledCleanup cleanup=new ControlledCleanup(1,1);
        HistoricalMigrationExportArtifact artifact=writer(cleanup).write(List.of(candidate()).iterator());

        IOException failure=assertThrows(IOException.class,artifact::close);
        assertEquals("Historical migration export could not be cleaned up",failure.getMessage());
        assertEquals(1,failure.getSuppressed().length);
        assertEquals("Historical migration export could not be cleaned up",failure.getSuppressed()[0].getMessage());
        assertFalse(failure.getMessage().contains(temporaryDirectory.toString()));
        assertFalse(failure.getSuppressed()[0].getMessage().contains(temporaryDirectory.toString()));
        assertFalse(failure.getMessage().contains("sensitive archive content"));
        assertFalse(failure.getSuppressed()[0].getMessage().contains("sensitive archive content"));
        assertEquals(1,cleanup.closeAttempts);
        assertEquals(1,cleanup.deleteAttempts);

        artifact.close();
        assertEquals(1,cleanup.closeAttempts);
        assertEquals(2,cleanup.deleteAttempts);
        assertNoRegularFiles();
    }

    private HistoricalMigrationExportArchiveWriter writer()
    {return new HistoricalMigrationExportArchiveWriter(temporaryDirectory,Clock.fixed(GENERATED_AT,ZoneOffset.UTC));}

    private HistoricalMigrationExportArchiveWriter writer(HistoricalMigrationExportArchiveCleanup cleanup)
    {return new HistoricalMigrationExportArchiveWriter(temporaryDirectory,Clock.fixed(GENERATED_AT,ZoneOffset.UTC),cleanup);}

    private static HistoricalMigrationCaseCandidate candidate()
    {return new HistoricalMigrationCaseCandidate(1L,"CASE-001","name","CIVIL","OPEN",null,null,null);}

    private void assertNoRegularFiles()
    {
        try(var paths=Files.list(temporaryDirectory)) { assertTrue(paths.noneMatch(Files::isRegularFile)); }
        catch(IOException exception) { throw new AssertionError(exception); }
    }

    private static Map<String,byte[]> zipEntries(byte[] bytes) throws IOException
    {
        Map<String,byte[]> entries=new LinkedHashMap<>();
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) entries.put(entry.getName(),zip.readAllBytes());
        }
        return entries;
    }

    private static String sha256(byte[] value) throws Exception
    {return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}

    private static final class ControlledCleanup implements HistoricalMigrationExportArchiveCleanup
    {
        private int remainingCloseFailures;
        private int remainingDeleteFailures;
        private int closeAttempts;
        private int deleteAttempts;

        private ControlledCleanup(int closeFailures,int deleteFailures)
        {remainingCloseFailures=closeFailures;remainingDeleteFailures=deleteFailures;}

        @Override public InputStream open(Path zip) throws IOException
        {
            return new FilterInputStream(Files.newInputStream(zip)) {
                @Override public void close() throws IOException
                {
                    closeAttempts++;
                    super.close();
                    if(remainingCloseFailures-- > 0)throw new IOException("failed to close "+zip+": sensitive archive content");
                }
            };
        }

        @Override public void delete(Path zip) throws IOException
        {
            deleteAttempts++;
            if(remainingDeleteFailures-- > 0)throw new IOException("failed to delete "+zip+": sensitive archive content");
            Files.deleteIfExists(zip);
        }
    }

    private static final class CountingCandidates implements Iterator<HistoricalMigrationCaseCandidate>
    {
        private final int size;
        private int index;
        private int nextCalls;
        private CountingCandidates(int size){this.size=size;}
        @Override public boolean hasNext(){return index<size;}
        @Override public HistoricalMigrationCaseCandidate next()
        {
            if(!hasNext())throw new NoSuchElementException();
            nextCalls++;
            long id=++index;
            return new HistoricalMigrationCaseCandidate(id,"CASE-"+id,"case "+id,"CIVIL","OPEN",null,null,null);
        }
    }
}
