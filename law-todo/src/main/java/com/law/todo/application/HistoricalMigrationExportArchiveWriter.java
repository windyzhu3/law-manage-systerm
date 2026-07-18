package com.law.todo.application;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.law.todo.application.view.HistoricalMigrationCaseCandidate;
import com.law.todo.application.view.HistoricalMigrationExportArtifact;
import com.law.todo.domain.TodoException;

@Component
public class HistoricalMigrationExportArchiveWriter
{
    private static final List<String> COLUMNS=List.of("case_id","case_no","case_name","case_type","case_status",
            "contract_id","main_lawyer_id","dept_id","proposed_business_line","exception_reason","review_status",
            "reviewer_user_id","reviewed_at");
    private static final byte[] BOM={(byte)0xEF,(byte)0xBB,(byte)0xBF};
    private static final List<String> ALLOWED_LINES=List.of("NON_LITIGATION","COMPREHENSIVE","EXECUTION");

    private final Path root;
    private final Clock clock;

    public HistoricalMigrationExportArchiveWriter(
            @Value("${todo.migration-export.temp-dir:${java.io.tmpdir}/law-todo-migration-export}") String root)
    { this(Path.of(root),Clock.systemUTC()); }

    HistoricalMigrationExportArchiveWriter(Path root,Clock clock)
    { this.root=root.toAbsolutePath().normalize();this.clock=clock; }

    public HistoricalMigrationExportArtifact write(Iterator<HistoricalMigrationCaseCandidate> candidates)
    {
        Path csv=null,zip=null;
        try {
            Files.createDirectories(root);
            csv=temporaryFile("historical-case-exceptions-", ".csv");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            long rows=writeCsv(csv,candidates,digest);
            String csvSha256=java.util.HexFormat.of().formatHex(digest.digest());
            Instant generatedAt=clock.instant();
            zip=temporaryFile("historical-migration-export-", ".zip");
            writeZip(zip,csv,manifest(rows,csvSha256,generatedAt),generatedAt);
            Files.deleteIfExists(csv);csv=null;
            long size=Files.size(zip);
            return new HistoricalMigrationExportArtifact(cleanupOnClose(zip),size,rows,csvSha256,generatedAt);
        } catch(Exception exception) {
            delete(csv);delete(zip);
            throw new TodoException("TODO_MIGRATION_EXPORT_FAILED","Historical migration export could not be generated");
        }
    }

    private Path temporaryFile(String prefix,String suffix) throws IOException
    {
        Path file=Files.createTempFile(root,prefix,suffix).toAbsolutePath().normalize();
        if(!file.startsWith(root)) {
            delete(file);
            throw new IOException("Temporary export file escaped the dedicated root");
        }
        return file;
    }

    private long writeCsv(Path csv,Iterator<HistoricalMigrationCaseCandidate> candidates,MessageDigest digest) throws IOException
    {
        long rows=0;
        try(OutputStream file=Files.newOutputStream(csv);DigestOutputStream output=new DigestOutputStream(file,digest)) {
            output.write(BOM);
            writeRow(output,COLUMNS);
            while(candidates.hasNext()) {
                HistoricalMigrationCaseCandidate candidate=candidates.next();
                writeRow(output,Arrays.asList(candidate.caseId(),candidate.caseNo(),candidate.caseName(),candidate.caseType(),candidate.caseStatus(),
                        candidate.contractId(),candidate.mainLawyerId(),candidate.deptId(),"","NO_REVIEWED_CLASSIFICATION","PENDING","",""));
                rows++;
            }
        }
        return rows;
    }

    private void writeZip(Path zip,Path csv,byte[] manifest,Instant generatedAt) throws IOException
    {
        try(OutputStream file=Files.newOutputStream(zip);ZipOutputStream output=new ZipOutputStream(file,StandardCharsets.UTF_8)) {
            writeZipEntry(output,"historical-case-exceptions.csv",csv,generatedAt);
            writeZipEntry(output,"manifest.json",manifest,generatedAt);
        }
    }

    private static void writeZipEntry(ZipOutputStream output,String name,Path source,Instant generatedAt) throws IOException
    {
        ZipEntry entry=new ZipEntry(name);
        entry.setTime(generatedAt.toEpochMilli());
        output.putNextEntry(entry);
        try(InputStream input=Files.newInputStream(source)) { input.transferTo(output); }
        output.closeEntry();
    }

    private static void writeZipEntry(ZipOutputStream output,String name,byte[] bytes,Instant generatedAt) throws IOException
    {
        ZipEntry entry=new ZipEntry(name);
        entry.setTime(generatedAt.toEpochMilli());
        output.putNextEntry(entry);
        output.write(bytes);
        output.closeEntry();
    }

    private static byte[] manifest(long rows,String csvSha256,Instant generatedAt)
    {
        Map<String,Object> manifest=new LinkedHashMap<>();
        manifest.put("generatedAt",generatedAt.toString());
        manifest.put("rowCount",rows);
        manifest.put("csvSha256",csvSha256);
        manifest.put("classificationState","UNREVIEWED");
        manifest.put("allowedBusinessLines",ALLOWED_LINES);
        return JSON.toJSONBytes(manifest);
    }

    private static void writeRow(OutputStream output,List<?> values) throws IOException
    {
        StringBuilder row=new StringBuilder();
        for(int index=0;index<values.size();index++) {
            if(index>0)row.append(',');
            row.append('"').append(escape(values.get(index))).append('"');
        }
        row.append("\r\n");
        output.write(row.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(Object value)
    {
        String text=value == null ? "" : String.valueOf(value);
        if(!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0))>=0)text="'"+text;
        return text.replace("\"","\"\"");
    }

    private static InputStream cleanupOnClose(Path zip) throws IOException
    {
        return new FilterInputStream(Files.newInputStream(zip)) {
            private boolean closed;
            @Override public void close() throws IOException
            {
                if(closed)return;
                closed=true;
                try {
                    super.close();
                    Files.deleteIfExists(zip);
                } catch(IOException exception) {
                    throw new IOException("Historical migration export could not be cleaned up");
                }
            }
        };
    }

    private static void delete(Path path)
    {
        if(path==null)return;
        try { Files.deleteIfExists(path); } catch(IOException ignored) { }
    }
}
