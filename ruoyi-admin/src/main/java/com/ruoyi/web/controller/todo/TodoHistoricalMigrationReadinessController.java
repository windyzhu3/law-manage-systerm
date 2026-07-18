package com.ruoyi.web.controller.todo;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.law.todo.application.TodoHistoricalMigrationExportService;
import com.law.todo.application.TodoHistoricalMigrationReadinessService;
import com.law.todo.application.TodoHistoricalMigrationPreflightService;
import com.law.todo.application.view.HistoricalMigrationExportArtifact;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;

@RestController
@RequestMapping("/todo/foundation-migration")
public class TodoHistoricalMigrationReadinessController
{
    private final TodoHistoricalMigrationReadinessService service;
    private final TodoHistoricalMigrationPreflightService preflight;
    private final TodoHistoricalMigrationExportService exports;
    public TodoHistoricalMigrationReadinessController(TodoHistoricalMigrationReadinessService service,
            TodoHistoricalMigrationPreflightService preflight,TodoHistoricalMigrationExportService exports)
    {this.service=service;this.preflight=preflight;this.exports=exports;}

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
    @Log(title="G-04鍘嗗彶杩佺Щ寮傚父娓呭崟",businessType=BusinessType.EXPORT)
    @GetMapping("/exception-export")
    public ResponseEntity<StreamingResponseBody> exceptionExport(
            @RequestParam(defaultValue="G-04") String gateCode)
    {
        HistoricalMigrationExportArtifact artifact=exports.export(gateCode);
        StreamingResponseBody body=output->{
            try(artifact) {artifact.input().transferTo(output);}
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
}
