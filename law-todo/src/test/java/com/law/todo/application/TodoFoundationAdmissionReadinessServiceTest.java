package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.law.todo.application.view.TodoFoundationAdmissionGateView;
import com.law.todo.mapper.TodoFoundationAdmissionReadinessMapper;

class TodoFoundationAdmissionReadinessServiceTest
{
    private final TodoFoundationAdmissionReadinessMapper mapper=mock(TodoFoundationAdmissionReadinessMapper.class);
    private final TodoFoundationResourceService resources=mock(TodoFoundationResourceService.class);
    private final TodoHistoricalMigrationReadinessService migrations=mock(TodoHistoricalMigrationReadinessService.class);
    private final TodoFileSecurityReadinessService fileSecurity=mock(TodoFileSecurityReadinessService.class);
    private final TodoFinanceReadinessService finance=mock(TodoFinanceReadinessService.class);
    private final TodoAcceptanceReadinessService acceptance=mock(TodoAcceptanceReadinessService.class);
    private final TodoFoundationAdmissionReadinessService service=new TodoFoundationAdmissionReadinessService(
            mapper,resources,migrations,fileSecurity,finance,acceptance);

    @BeforeEach
    void baseline()
    {
        when(mapper.selectAdmissionFacts()).thenReturn(facts(false));
        when(resources.gateReady("G-02")).thenReturn(false);
        when(migrations.gateReady("G-04")).thenReturn(false);
        when(fileSecurity.gateReady("G-05")).thenReturn(false);
        when(finance.gateReady("G-06")).thenReturn(false);
        when(acceptance.gateReady("G-07")).thenReturn(false);
    }

    @Test
    void repository_baseline_is_truthfully_reported_as_two_of_eight_not_admitted()
    {
        var report=service.readiness();

        assertEquals("NOT_ADMITTED",report.overallStatus());
        assertFalse(report.admitted());
        assertEquals(8,report.totalGateCount());
        assertEquals(2,report.readyGateCount());
        assertEquals("READY",gate(report.gates(),"G-03").status());
        assertEquals("READY",gate(report.gates(),"G-08").status());
        assertEquals("BLOCKED",gate(report.gates(),"G-01").status());
        assertTrue(gate(report.gates(),"G-01").blockers().contains("12 个阻断决策尚未全部分配责任人、责任角色和截止时间"));
        assertEquals("OPEN",gate(report.gates(),"G-05").evidenceStatus());
    }

    @Test
    void all_eight_gates_are_required_before_phase_one_business_implementation_is_admitted()
    {
        when(mapper.selectAdmissionFacts()).thenReturn(facts(true));
        when(resources.gateReady("G-02")).thenReturn(true);
        when(migrations.gateReady("G-04")).thenReturn(true);
        when(fileSecurity.gateReady("G-05")).thenReturn(true);
        when(finance.gateReady("G-06")).thenReturn(true);
        when(acceptance.gateReady("G-07")).thenReturn(true);

        var report=service.readiness();

        assertTrue(report.admitted());
        assertEquals("ADMITTED_FOR_PHASE_ONE_BUSINESS_IMPLEMENTATION",report.overallStatus());
        assertEquals(8,report.readyGateCount());
        assertTrue(report.gates().stream().allMatch(TodoFoundationAdmissionGateView::ready));
    }

    @Test
    void approved_evidence_never_bypasses_a_failed_live_gate()
    {
        Map<String,Object> facts=facts(true);
        when(mapper.selectAdmissionFacts()).thenReturn(facts);
        when(resources.gateReady("G-02")).thenReturn(true);
        when(migrations.gateReady("G-04")).thenReturn(true);
        when(fileSecurity.gateReady("G-05")).thenReturn(false);
        when(finance.gateReady("G-06")).thenReturn(true);
        when(acceptance.gateReady("G-07")).thenReturn(true);

        TodoFoundationAdmissionGateView gate=gate(service.readiness().gates(),"G-05");

        assertFalse(gate.ready());
        assertEquals("APPROVED",gate.evidenceStatus());
        assertTrue(gate.blockers().contains("文件安全运行态或来源要求尚未全部就绪"));
    }

    @Test
    void extra_decisions_or_incomplete_per_template_acceptance_references_block_admission()
    {
        Map<String,Object> drifted=facts(true);
        drifted.put("decision_total",13);
        drifted.put("prd_acceptance_ref_total",149);
        when(mapper.selectAdmissionFacts()).thenReturn(drifted);
        when(resources.gateReady("G-02")).thenReturn(true);
        when(migrations.gateReady("G-04")).thenReturn(true);
        when(fileSecurity.gateReady("G-05")).thenReturn(true);
        when(finance.gateReady("G-06")).thenReturn(true);
        when(acceptance.gateReady("G-07")).thenReturn(true);

        var report=service.readiness();

        assertFalse(report.admitted());
        assertEquals(6,report.readyGateCount());
        assertTrue(gate(report.gates(),"G-01").blockers().contains("Q-001～Q-012 决策目录必须恰好为 12 项"));
        assertTrue(gate(report.gates(),"G-03").blockers().contains("25 个定义包必须恰好包含 150 个验收引用"));
    }

    private static TodoFoundationAdmissionGateView gate(java.util.List<TodoFoundationAdmissionGateView> gates,String code)
    {return gates.stream().filter(value->code.equals(value.gateCode())).findFirst().orElseThrow();}

    private static Map<String,Object> facts(boolean ready)
    {
        Map<String,Object> values=new HashMap<>();
        values.put("decision_total",12);values.put("decision_accountable",ready?12:0);
        values.put("phase_one_total",8);values.put("phase_one_closed",ready?8:0);
        values.put("prd_total",25);values.put("prd_ready",25);values.put("prd_valid_definition",25);
        values.put("prd_acceptance_ref_total",150);
        for(String gate:java.util.List.of("g02","g04","g05","g06","g07"))
            values.put(gate+"_evidence_status",ready?"APPROVED":"OPEN");
        values.put("foundation_migration_present",1);values.put("failed_migration_count",0);
        values.put("core_idempotency_index_count",4);
        return values;
    }
}
