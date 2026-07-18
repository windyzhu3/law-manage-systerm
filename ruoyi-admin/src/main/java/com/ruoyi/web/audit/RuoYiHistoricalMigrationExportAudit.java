package com.ruoyi.web.audit;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.enums.BusinessStatus;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.enums.OperatorType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.system.domain.SysOperLog;
import com.ruoyi.system.service.ISysOperLogService;

@Component
public class RuoYiHistoricalMigrationExportAudit implements HistoricalMigrationExportAudit
{
    private static final String TITLE="G-04历史迁移异常清单";
    private static final String METHOD="TodoHistoricalMigrationReadinessController.exceptionExport()";

    private final ISysOperLogService logs;
    private final Supplier<Metadata> metadata;
    private final LongSupplier milliseconds;

    @Autowired
    public RuoYiHistoricalMigrationExportAudit(ISysOperLogService logs)
    {this(logs,RuoYiHistoricalMigrationExportAudit::captureMetadata,System::currentTimeMillis);}

    RuoYiHistoricalMigrationExportAudit(ISysOperLogService logs,Supplier<Metadata> metadata,
            LongSupplier milliseconds)
    {this.logs=logs;this.metadata=metadata;this.milliseconds=milliseconds;}

    @Override public Transfer begin(long rowCount)
    {
        long startedAt=milliseconds.getAsLong();
        Metadata captured=metadata.get();
        AtomicBoolean recorded=new AtomicBoolean();
        return new Transfer() {
            @Override public void success(){record("SUCCESS",rowCount,startedAt,captured,recorded);}
            @Override public void failure(){record("FAILURE",rowCount,startedAt,captured,recorded);}
        };
    }

    private void record(String outcome,long rowCount,long startedAt,Metadata captured,AtomicBoolean recorded)
    {
        if(!recorded.compareAndSet(false,true))return;
        SysOperLog log=new SysOperLog();
        log.setTitle(TITLE);
        log.setBusinessType(BusinessType.EXPORT.ordinal());
        log.setOperatorType(OperatorType.MANAGE.ordinal());
        log.setMethod(METHOD);
        log.setRequestMethod(StringUtils.substring(captured.requestMethod(),0,10));
        log.setOperName(StringUtils.substring(captured.operatorName(),0,50));
        log.setDeptName(StringUtils.substring(captured.departmentName(),0,50));
        log.setOperIp(StringUtils.substring(captured.ipAddress(),0,128));
        log.setOperUrl(StringUtils.substring(captured.requestUri(),0,255));
        log.setOperParam("rowCount="+rowCount);
        log.setJsonResult("outcome="+outcome+",rowCount="+rowCount);
        log.setStatus("SUCCESS".equals(outcome)?BusinessStatus.SUCCESS.ordinal():BusinessStatus.FAIL.ordinal());
        if(!"SUCCESS".equals(outcome))log.setErrorMsg("Historical migration export transfer failed");
        log.setCostTime(Math.max(0L,milliseconds.getAsLong()-startedAt));
        logs.insertOperlog(log);
    }

    private static Metadata captureMetadata()
    {
        LoginUser login=SecurityUtils.getLoginUser();
        SysUser user=login.getUser();
        String department=user!=null&&user.getDept()!=null?user.getDept().getDeptName():null;
        HttpServletRequest request=ServletUtils.getRequest();
        return new Metadata(login.getUsername(),department,IpUtils.getIpAddr(request),request.getRequestURI(),
                request.getMethod());
    }

    record Metadata(String operatorName,String departmentName,String ipAddress,String requestUri,
            String requestMethod) { }
}
