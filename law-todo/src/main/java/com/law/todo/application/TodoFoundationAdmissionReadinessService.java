package com.law.todo.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.law.todo.application.view.TodoFoundationAdmissionGateView;
import com.law.todo.application.view.TodoFoundationAdmissionReadinessView;
import com.law.todo.mapper.TodoFoundationAdmissionReadinessMapper;

@Service
public class TodoFoundationAdmissionReadinessService
{
    private static final int EXPECTED_DECISIONS=12;
    private static final int EXPECTED_PHASE_ONE_DECISIONS=8;
    private static final int EXPECTED_PRD_DEFINITIONS=25;
    private static final int EXPECTED_ACCEPTANCE_REFS=150;
    private static final int EXPECTED_IDEMPOTENCY_INDEXES=4;

    private final TodoFoundationAdmissionReadinessMapper mapper;
    private final TodoFoundationResourceService resources;
    private final TodoHistoricalMigrationReadinessService migrations;
    private final TodoFileSecurityReadinessService fileSecurity;
    private final TodoFinanceReadinessService finance;
    private final TodoAcceptanceReadinessService acceptance;

    public TodoFoundationAdmissionReadinessService(TodoFoundationAdmissionReadinessMapper mapper,
            TodoFoundationResourceService resources,TodoHistoricalMigrationReadinessService migrations,
            TodoFileSecurityReadinessService fileSecurity,TodoFinanceReadinessService finance,
            TodoAcceptanceReadinessService acceptance)
    {
        this.mapper=mapper;this.resources=resources;this.migrations=migrations;this.fileSecurity=fileSecurity;
        this.finance=finance;this.acceptance=acceptance;
    }

    @Transactional(readOnly=true)
    public TodoFoundationAdmissionReadinessView readiness()
    {
        Map<String,Object> facts=mapper.selectAdmissionFacts();
        if(facts==null)facts=Map.of();
        List<TodoFoundationAdmissionGateView> gates=new ArrayList<>();
        gates.add(decisions(facts));
        gates.add(evidenceGate("G-02","业务字典与稳定角色键",resources.gateReady("G-02"),evidence(facts,"g02"),
                "业务字典或稳定角色键尚未全部确认并落地"));
        gates.add(definitions(facts));
        gates.add(evidenceGate("G-04","历史数据迁移方案",migrations.gateReady("G-04"),evidence(facts,"g04"),
                "历史迁移方案或运行态校验尚未全部就绪"));
        gates.add(evidenceGate("G-05","文件中心安全验收",fileSecurity.gateReady("G-05"),evidence(facts,"g05"),
                "文件安全运行态或来源要求尚未全部就绪"));
        gates.add(evidenceGate("G-06","收费节点与风险公式",finance.gateReady("G-06"),evidence(facts,"g06"),
                "财务结构、公式决策或签字尚未全部就绪"));
        gates.add(evidenceGate("G-07","阶段一验收包",acceptance.gateReady("G-07"),evidence(facts,"g07"),
                "阶段一场景、黄金数据、AT 映射或独立评审尚未全部就绪"));
        gates.add(migrationsAndIdempotency(facts));
        int ready=(int)gates.stream().filter(TodoFoundationAdmissionGateView::ready).count();
        boolean admitted=ready==gates.size();
        return new TodoFoundationAdmissionReadinessView(admitted?"ADMITTED_FOR_PHASE_ONE_BUSINESS_IMPLEMENTATION":"NOT_ADMITTED",
                admitted,ready,gates.size(),List.copyOf(gates));
    }

    private TodoFoundationAdmissionGateView decisions(Map<String,Object> facts)
    {
        int total=integer(facts,"decision_total"),accountable=integer(facts,"decision_accountable");
        int phaseTotal=integer(facts,"phase_one_total"),closed=integer(facts,"phase_one_closed");
        List<String> blockers=new ArrayList<>();
        if(total!=EXPECTED_DECISIONS)blockers.add("Q-001～Q-012 决策目录必须恰好为 12 项");
        if(accountable!=EXPECTED_DECISIONS)blockers.add("12 个阻断决策尚未全部分配责任人、责任角色和截止时间");
        if(phaseTotal!=EXPECTED_PHASE_ONE_DECISIONS)blockers.add("阶段一阻断决策目录必须恰好为 8 项");
        if(closed!=EXPECTED_PHASE_ONE_DECISIONS)blockers.add("8 个阶段一阻断决策尚未全部关闭并冻结结论");
        boolean ready=blockers.isEmpty();
        return gate("G-01","阻断决策责任与冻结",ready,ready,"NOT_REQUIRED",
                Math.min(accountable,EXPECTED_DECISIONS)+Math.min(closed,EXPECTED_PHASE_ONE_DECISIONS),20,
                accountable+"/12 已分配，"+closed+"/8 阶段一已关闭",blockers);
    }

    private TodoFoundationAdmissionGateView definitions(Map<String,Object> facts)
    {
        int total=integer(facts,"prd_total"),readyCount=integer(facts,"prd_ready");
        int valid=integer(facts,"prd_valid_definition"),refs=integer(facts,"prd_acceptance_ref_total");
        List<String> blockers=new ArrayList<>();
        if(total!=EXPECTED_PRD_DEFINITIONS)blockers.add("TD-001～TD-025 定义目录必须恰好为 25 项");
        if(readyCount!=EXPECTED_PRD_DEFINITIONS)blockers.add("25 个定义包尚未全部标记 READY");
        if(valid!=EXPECTED_PRD_DEFINITIONS)blockers.add("25 个定义包尚未全部包含事件、Owner、DoD、SLA、路由和 UI 契约");
        if(refs!=EXPECTED_ACCEPTANCE_REFS)blockers.add("25 个定义包必须恰好包含 150 个验收引用");
        boolean ready=blockers.isEmpty();
        int completed=Math.min(Math.min(readyCount,valid),refs/6);
        return gate("G-03","TD-001～TD-025 定义包",ready,ready,"NOT_REQUIRED",completed,25,
                readyCount+"/25 READY，"+refs+"/150 验收引用",blockers);
    }

    private TodoFoundationAdmissionGateView evidenceGate(String code,String title,boolean technical,String evidence,
            String technicalBlocker)
    {
        List<String> blockers=new ArrayList<>();
        if(!technical)blockers.add(technicalBlocker);
        if(!"APPROVED".equals(evidence))blockers.add("独立准入证据尚未 APPROVED");
        boolean ready=technical&&"APPROVED".equals(evidence);
        return gate(code,title,ready,technical,evidence,(technical?1:0)+("APPROVED".equals(evidence)?1:0),2,
                "技术/来源门禁 "+(technical?"READY":"BLOCKED")+"，证据 "+evidence,blockers);
    }

    private TodoFoundationAdmissionGateView migrationsAndIdempotency(Map<String,Object> facts)
    {
        boolean migration=integer(facts,"foundation_migration_present")==1;
        int failures=integer(facts,"failed_migration_count"),indexes=integer(facts,"core_idempotency_index_count");
        List<String> blockers=new ArrayList<>();
        if(!migration)blockers.add("Foundation 终端 Flyway 迁移 0.20.22 尚未成功执行");
        if(failures!=0)blockers.add("Flyway 存在失败迁移记录");
        if(indexes!=EXPECTED_IDEMPOTENCY_INDEXES)blockers.add("核心事件、待办和动作幂等唯一索引必须恰好为 4 项");
        boolean ready=blockers.isEmpty();
        return gate("G-08","Flyway 与幂等约束",ready,ready,"NOT_REQUIRED",
                (migration?1:0)+(failures==0?1:0)+Math.min(indexes,EXPECTED_IDEMPOTENCY_INDEXES),6,
                "迁移 "+(migration?"READY":"MISSING")+"，失败 "+failures+"，幂等索引 "+indexes+"/4",blockers);
    }

    private static TodoFoundationAdmissionGateView gate(String code,String title,boolean ready,boolean technical,
            String evidence,int completed,int total,String summary,List<String> blockers)
    {return new TodoFoundationAdmissionGateView(code,title,ready?"READY":"BLOCKED",ready,technical,evidence,
            completed,total,summary,List.copyOf(blockers));}

    private static String evidence(Map<String,Object> facts,String gate)
    {String value=text(value(facts,gate+"_evidence_status",camel(gate+"_evidence_status")));return value==null?"MISSING":value;}
    private static int integer(Map<String,Object> facts,String key)
    {Object value=value(facts,key,camel(key));return value==null?0:Integer.parseInt(String.valueOf(value));}
    private static Object value(Map<String,Object> facts,String snake,String camel)
    {return facts.containsKey(snake)?facts.get(snake):facts.get(camel);}
    private static String text(Object value){return value==null?null:String.valueOf(value);}
    private static String camel(String value)
    {
        StringBuilder result=new StringBuilder();boolean upper=false;
        for(char c:value.toCharArray()){if(c=='_'){upper=true;}else{result.append(upper?Character.toUpperCase(c):c);upper=false;}}
        return result.toString();
    }
}
