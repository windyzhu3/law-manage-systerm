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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        assertEquals(List.of("schemaVersion","gateCode","fileName","rowCount","csvSha256","generatedAt",
                "classificationState","allowedBusinessLines"),new ArrayList<>(manifest.keySet()));
        assertEquals(1,manifest.getIntValue("schemaVersion"));
        assertEquals("G-04",manifest.getString("gateCode"));
        assertEquals("historical-case-exceptions.csv",manifest.getString("fileName"));
        assertEquals(2,manifest.getLongValue("rowCount"));
        assertEquals(artifact.csvSha256(),manifest.getString("csvSha256"));
        assertEquals(GENERATED_AT.toString(),manifest.getString("generatedAt"));
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

    @ParameterizedTest
    @ValueSource(ints={1,2})
    void closeRetriesTransientDeletionWithinOneProductionCall(int transientFailures) throws Exception
    {
        ControlledCleanup cleanup=new ControlledCleanup(0,transientFailures);
        HistoricalMigrationExportArtifact artifact=writer(cleanup).write(List.of(candidate()).iterator());

        artifact.close();
        assertEquals(1,cleanup.closeAttempts);
        assertEquals(transientFailures+1,cleanup.deleteAttempts);
        assertEquals(0,cleanup.fallbackRegistrations);
        assertNoRegularFiles();

        artifact.close();
        assertEquals(1,cleanup.closeAttempts);
        assertEquals(transientFailures+1,cleanup.deleteAttempts);
    }

    @Test void generationFailureUsesTheSameBoundedDeletionRetry()
    {
        ControlledCleanup cleanup=new ControlledCleanup(0,2,true);
        Iterator<HistoricalMigrationCaseCandidate> failing=new Iterator<>() {
            @Override public boolean hasNext(){return true;}
            @Override public HistoricalMigrationCaseCandidate next(){throw new IllegalStateException("cursor failed");}
        };

        TodoException failure=assertThrows(TodoException.class,()->writer(cleanup).write(failing));
        assertEquals("TODO_MIGRATION_EXPORT_FAILED",failure.getBusinessCode());
        assertEquals("Historical migration export could not be generated",failure.getMessage());
        assertEquals(3,cleanup.csvDeleteAttempts);
        assertEquals(0,cleanup.fallbackRegistrations);
        assertNoRegularFiles();
    }

    @Test void permanentDeletionFailureIsSanitizedAndRegistersOneExitFallback() throws Exception
    {
        ControlledCleanup cleanup=new ControlledCleanup(0,Integer.MAX_VALUE);
        HistoricalMigrationExportArtifact artifact=writer(cleanup).write(List.of(candidate()).iterator());

        IOException failure=assertThrows(IOException.class,artifact::close);
        assertEquals("Historical migration export could not be cleaned up",failure.getMessage());
        assertFalse(failure.getMessage().contains(temporaryDirectory.toString()));
        assertFalse(failure.getMessage().contains("sensitive archive content"));
        assertEquals(3,cleanup.deleteAttempts);
        assertEquals(1,cleanup.fallbackRegistrations);

        IOException repeated=assertThrows(IOException.class,artifact::close);
        assertEquals("Historical migration export could not be cleaned up",repeated.getMessage());
        assertEquals(6,cleanup.deleteAttempts);
        assertEquals(1,cleanup.fallbackRegistrations);
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
        private int csvDeleteAttempts;
        private int fallbackRegistrations;
        private final boolean failCsvDeletion;

        private ControlledCleanup(int closeFailures,int deleteFailures)
        {this(closeFailures,deleteFailures,false);}

        private ControlledCleanup(int closeFailures,int deleteFailures,boolean failCsvDeletion)
        {remainingCloseFailures=closeFailures;remainingDeleteFailures=deleteFailures;this.failCsvDeletion=failCsvDeletion;}

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
            boolean csv=zip.toString().endsWith(".csv");
            if(csv)csvDeleteAttempts++;else deleteAttempts++;
            if((!csv||failCsvDeletion)&&remainingDeleteFailures-- > 0)
                throw new IOException("failed to delete "+zip+": sensitive archive content");
            Files.deleteIfExists(zip);
        }

        @Override public void registerDeleteOnExit(Path zip)
        {fallbackRegistrations++;}
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
