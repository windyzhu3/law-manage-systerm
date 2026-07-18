package com.ruoyi.web.controller.todo;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.law.todo.application.TodoHistoricalMigrationExportService;
import com.law.todo.application.TodoHistoricalMigrationReadinessService;
import com.law.todo.application.TodoHistoricalMigrationPreflightService;
import com.law.todo.application.view.HistoricalMigrationExportArtifact;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.web.audit.HistoricalMigrationExportAudit;

@RestController
@RequestMapping("/todo/foundation-migration")
public class TodoHistoricalMigrationReadinessController
{
    private static final Logger LOG=LoggerFactory.getLogger(TodoHistoricalMigrationReadinessController.class);
    private final TodoHistoricalMigrationReadinessService service;
    private final TodoHistoricalMigrationPreflightService preflight;
    private final TodoHistoricalMigrationExportService exports;
    private final HistoricalMigrationExportAudit audit;
    public TodoHistoricalMigrationReadinessController(TodoHistoricalMigrationReadinessService service,
            TodoHistoricalMigrationPreflightService preflight,TodoHistoricalMigrationExportService exports,
            HistoricalMigrationExportAudit audit)
    {this.service=service;this.preflight=preflight;this.exports=exports;this.audit=audit;}

    @PreAuthorize("@ss.hasPermi('todo:admission:view')")
    @GetMapping
    public AjaxResult readiness(@RequestParam(defaultValue="G-04") String gateCode)
    {
        return AjaxResult.success(service.readiness(gateCode));
    }

    @PreAuthorize("@ss.hasPermi('todo:admission:view')")
    @GetMapping("/preflight")
    public AjaxResult preflight(@RequestParam(defaultValue="G-04") String gateCode)
    {
        return AjaxResult.success(preflight.preflight(gateCode));
    }

    @PreAuthorize("@ss.hasPermi('todo:admission:export')")
    @GetMapping("/exception-export")
    public ResponseEntity<StreamingResponseBody> exceptionExport(
            @RequestParam(defaultValue="G-04") String gateCode)
    {
        HistoricalMigrationExportArtifact artifact=exports.export(gateCode);
        HistoricalMigrationExportAudit.Transfer transferAudit=beginAudit(artifact.rowCount());
        StreamingResponseBody body=output->{
            try(artifact) {artifact.input().transferTo(output);}
            catch(IOException|RuntimeException failure) {
                recordAudit(transferAudit::failure);
                throw failure;
            }
            recordAudit(transferAudit::success);
        };
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(artifact.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"g04-historical-case-preflight.zip\"")
                .header("X-Exception-Row-Count",String.valueOf(artifact.rowCount()))
                .header("X-Exception-CSV-SHA256",artifact.csvSha256())
                .header(HttpHeaders.CACHE_CONTROL,"no-store")
                .header("X-Content-Type-Options","nosniff")
                .body(body);
    }

    private HistoricalMigrationExportAudit.Transfer beginAudit(long rowCount)
    {
        try {return audit.begin(rowCount);}
        catch(RuntimeException failure) {
            LOG.warn("Historical migration export audit metadata could not be captured");
            return new HistoricalMigrationExportAudit.Transfer() {
                @Override public void success(){ }
                @Override public void failure(){ }
            };
        }
    }

    private static void recordAudit(Runnable operation)
    {
        try {operation.run();}
        catch(RuntimeException failure) {LOG.warn("Historical migration export audit could not be recorded");}
    }
}
