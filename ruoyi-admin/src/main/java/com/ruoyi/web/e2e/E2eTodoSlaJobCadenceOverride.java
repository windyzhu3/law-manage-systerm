package com.ruoyi.web.e2e;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import com.ruoyi.quartz.domain.SysJob;
import com.ruoyi.quartz.service.ISysJobService;

/**
 * Speeds up the existing production SLA worker only in an explicitly enabled
 * isolated E2E process. The persisted production cadence remains unchanged.
 */
@Component
@Profile("e2e")
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(prefix="foundation.test-identities",name="enabled",havingValue="true")
public class E2eTodoSlaJobCadenceOverride implements ApplicationRunner
{
    static final String INVOKE_TARGET="todoSlaTask.scan";
    static final String E2E_CRON="0/10 * * * * ?";
    private final ISysJobService jobs;

    public E2eTodoSlaJobCadenceOverride(ISysJobService jobs){this.jobs=jobs;}

    @Override public void run(ApplicationArguments arguments) throws Exception
    {
        SysJob filter=new SysJob();filter.setInvokeTarget(INVOKE_TARGET);
        List<SysJob> exact=jobs.selectJobList(filter).stream()
            .filter(job->INVOKE_TARGET.equals(job.getInvokeTarget())).toList();
        if(exact.size()!=1)
            throw new IllegalStateException("Expected exactly one Todo SLA worker, found "+exact.size());
        SysJob job=exact.get(0);
        if(E2E_CRON.equals(job.getCronExpression())&&"0".equals(job.getStatus()))return;
        job.setCronExpression(E2E_CRON);
        job.setStatus("0");
        job.setUpdateBy("foundation-e2e");
        job.setRemark("E2E-only ten-second Todo SLA cadence");
        if(jobs.updateJob(job)!=1)
            throw new IllegalStateException("Todo SLA E2E cadence override was not persisted");
    }
}
