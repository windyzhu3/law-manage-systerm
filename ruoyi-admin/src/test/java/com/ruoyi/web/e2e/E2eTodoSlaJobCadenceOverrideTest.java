package com.ruoyi.web.e2e;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.ruoyi.quartz.domain.SysJob;
import com.ruoyi.quartz.service.ISysJobService;

class E2eTodoSlaJobCadenceOverrideTest
{
    @Test
    void productionProfileNeverRegistersTheOverride()
    {
        runner(mock(ISysJobService.class))
            .withPropertyValues("spring.profiles.active=prod",
                "foundation.test-identities.enabled=true")
            .run(context->assertFalse(context.containsBean("e2eTodoSlaJobCadenceOverride")));
    }

    @Test
    void e2eProfileRequiresTheExplicitIdentityFeatureGate()
    {
        runner(mock(ISysJobService.class))
            .withPropertyValues("spring.profiles.active=e2e")
            .run(context->assertFalse(context.containsBean("e2eTodoSlaJobCadenceOverride")));
    }

    @Test
    void e2eOverrideTargetsTheExistingWorkerAndIsIdempotent() throws Exception
    {
        ISysJobService service=mock(ISysJobService.class);
        SysJob worker=worker("0 0/5 * * * ?","0");
        when(service.selectJobList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(worker));
        when(service.updateJob(worker)).thenReturn(1);

        runner(service).withPropertyValues("spring.profiles.active=e2e",
                "foundation.test-identities.enabled=true")
            .run(context->{
                E2eTodoSlaJobCadenceOverride override=
                    context.getBean(E2eTodoSlaJobCadenceOverride.class);
                assertNotNull(override);
                try
                {
                    override.run(null);
                    assertSame(worker,service.selectJobList(new SysJob()).get(0));
                    override.run(null);
                    verify(service,times(1)).updateJob(worker);
                }
                catch(Exception failure){throw new AssertionError(failure);}
            });
    }

    @Test
    void refusesAmbiguousWorkerMatches()
    {
        ISysJobService service=mock(ISysJobService.class);
        when(service.selectJobList(org.mockito.ArgumentMatchers.any()))
            .thenReturn(List.of(worker("0 0/5 * * * ?","0"),worker("0 0/5 * * * ?","0")));
        E2eTodoSlaJobCadenceOverride override=new E2eTodoSlaJobCadenceOverride(service);
        assertThrows(IllegalStateException.class,()->override.run(null));
    }

    private ApplicationContextRunner runner(ISysJobService service)
    {
        return new ApplicationContextRunner()
            .withUserConfiguration(E2eTodoSlaJobCadenceOverride.class)
            .withBean(ISysJobService.class,()->service);
    }

    private SysJob worker(String cron,String status)
    {
        SysJob job=new SysJob();job.setJobId(5L);job.setJobName("Todo SLA");
        job.setJobGroup("LAW");job.setInvokeTarget(E2eTodoSlaJobCadenceOverride.INVOKE_TARGET);
        job.setCronExpression(cron);job.setStatus(status);job.setConcurrent("1");
        return job;
    }
}
