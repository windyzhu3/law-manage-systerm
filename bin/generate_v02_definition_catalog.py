#!/usr/bin/env python3
"""Generate the reviewed v0.2 PRD definition catalogue.

The JSON resources are definition *draft packages*.  Runtime route graphs need
database-assigned template version ids, so the executable definition keeps an
empty routing graph while ``routingPlan`` preserves the complete symbolic PRD
route.  Publication remains blocked until the listed decisions and business
dependencies are resolved and the symbolic plan is bound to published version
ids.
"""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RESOURCE_DIR = ROOT / "law-todo/src/main/resources/todo-definitions/v0.2"
MIGRATION = ROOT / "ruoyi-admin/src/main/resources/db/migration/V0_20_10__v02_prd_definition_catalog.sql"
MATRIX = ROOT / "doc/v0.2-foundation-template-matrix.md"


DECISIONS = {
    "Q-001": "案管分类标签是否由案管单人确认，还是需案管主管复核？",
    "Q-002": "非诉业务是否完全不进入档案管理员归档？是否仍需电子材料留存？",
    "Q-003": "执行案件中“一级助理/二级助理/执行部长”的组织角色是否新增？与原一级律师/二级律师/律师助理如何对应？",
    "Q-004": "合伙人律师分配与律师助理初评是否并存？如并存，前后顺序是什么？",
    "Q-005": "拒接理由①由一级律师审核后，是否仍保留合伙人最终兜底？",
    "Q-006": "外呼软件是否确定接入？APP直连ERP是否为P0还是P1？",
    "Q-007": "T0拨号次数自定义的默认策略是什么？是否按渠道/业务类型/销售组配置？",
    "Q-008": "无效线索/客户分级标签的等级和枚举值如何定义？",
    "Q-009": "进度节点型收费的节点清单、金额计算方式、催收Owner如何定义？",
    "Q-010": "T10庭审笔录是否必须上传才允许进入裁判文书跟进？",
    "Q-011": "裁判文书跟进周期从15日改为30日后，是否所有综法案件统一30日？",
    "Q-012": "风险代理费计算规则是否由系统自动算，还是二级律师填写、一级审核？",
}


def role(role_key: str, fallback: dict | None = None) -> dict:
    value = {"type": "ROLE", "roleKey": role_key}
    if fallback:
        value["fallback"] = fallback
    return value


def payload(field: str, fallback_role: str) -> dict:
    return {"type": "PAYLOAD", "field": field, "fallback": role(fallback_role)}


def supervisor(source_field: str, fallback_role: str) -> dict:
    return {"type": "SUPERVISOR", "sourceField": source_field, "levels": 1,
            "fallback": role(fallback_role)}


def fields(*items: tuple[str, str, str]) -> list[dict]:
    return [{"key": key, "type": kind, "label": label} for key, kind, label in items]


def material(kind: str, minimum: int = 1, label: str | None = None) -> dict:
    value = {"type": kind, "minCount": minimum}
    if label:
        value["label"] = label
    return value


def escalation(prefix: str, thresholds: tuple[int, ...] = (80, 100, 150)) -> list[dict]:
    return [{"config": {"ruleKey": f"{prefix}-sla-{percent}", "actionType": "ESCALATE",
                         "capability": "ESCALATE", "triggerAt": f"SLA_{percent}",
                         "maxAttempts": 3, "retryDelayMinutes": 5,
                         "claimTimeoutMinutes": 15}}
            for percent in thresholds]


def business_dependency(code: str, reason: str, status: str = "MISSING") -> dict:
    return {"code": code, "repositoryStatus": status, "reason": reason}


def route(start: str, *transitions: dict, notes: list[str] | None = None) -> dict:
    return {"binding": "SYMBOLIC_TEMPLATE_CODE", "start": start,
            "transitions": list(transitions), "notes": notes or []}


def transition(on: str, to: str | list[str], condition: str | None = None,
               action: str | None = None) -> dict:
    value: dict = {"on": on, "to": [to] if isinstance(to, str) else to}
    if condition:
        value["condition"] = condition
    if action:
        value["businessAction"] = action
    return value


def item(code: str, name: str, stage: str, business_type: str, event: str,
         owner: dict, required: list[str], ui_fields: list[dict], sla: dict,
         routing_plan: dict, decisions: list[str], dependencies: list[dict],
         handler_candidate: str | None = None, materials: list[dict] | None = None,
         auto_actions: list[dict] | None = None, extra_dod: dict | None = None) -> dict:
    acceptance = [f"AT-{code}-OWNER", f"AT-{code}-SLA", f"AT-{code}-DOD",
                  f"AT-{code}-ROUTE", f"AT-{code}-HANDLER", f"AT-{code}-UI"]
    blockers = [{"type": "DECISION", "ref": ref, "reason": DECISIONS[ref]}
                for ref in decisions]
    blockers += [{"type": "BUSINESS_DEPENDENCY", "ref": dep["code"],
                  "reason": dep["reason"]}
                 for dep in dependencies if dep["repositoryStatus"] != "PRESENT"]
    dod = {"requiredFields": required, "materials": materials or []}
    if extra_dod:
        dod.update(extra_dod)
    definition = {
        "schemaVersion": 1,
        "templateCode": code,
        "event": {"eventType": event, "payloadVersion": 1, "condition": {}},
        "owner": {"config": owner},
        "dod": {"config": dod},
        "sla": {"config": sla},
        "ui": {"config": {"formCode": code, "businessType": business_type,
                             "fields": ui_fields}},
        # Version ids do not exist until migration.  The symbolic route above is
        # deliberately not misrepresented as an executable runtime graph.
        "routing": {"config": {}},
        "autoActions": auto_actions or [],
        "decisionRefs": decisions,
        "acceptanceRefs": acceptance,
    }
    return {
        "templateCode": code,
        "templateName": name,
        "stage": stage,
        "businessType": business_type,
        "definitionPackageState": "READY",
        "foundationState": "BLOCKED" if blockers else "READY",
        "productionState": "BLOCKED" if blockers else "READY",
        "definition": definition,
        "routingPlan": routing_plan,
        "handlerCapability": {
            "requiredCode": f"{code}_COMPLETE",
            "repositoryStatus": "ADAPTER_REQUIRED" if handler_candidate else "MISSING",
            "existingCandidate": handler_candidate,
            "requireBusinessWriteback": True,
        },
        "businessDependencies": dependencies,
        "blockers": blockers,
        "acceptanceRefs": acceptance,
    }


TEMPLATES = [
    item("TD-001", "首联待办", "阶段1-2", "LEAD", "LEAD_ASSIGNED",
         payload("ownerId", "sales"), ["contactResult", "contactedAt"],
         fields(("contactResult", "dict", "首联结果"), ("contactedAt", "datetime", "联系时间"),
                ("name", "text", "姓名"), ("city", "text", "城市"),
                ("demand", "textarea", "诉求"), ("visited", "dict", "是否到所")),
         {"calendarCode": "DEFAULT", "minutes": 30, "thresholds": [80, 100, 150]},
         route("TD-001", transition("VALID", "TD-004", action="WRITE_LEAD_VALID"),
               transition("SUSPECT_INVALID", "TD-002", action="MARK_SUSPECT_INVALID"),
               transition("UNREACHABLE", "TD-003", action="START_RETRY")),
         ["Q-006"],
         [business_dependency("TD001_HANDLER_ALIAS", "现有LEAD_FIRST_CONTACT处理器未绑定TD-001", "ADAPTER_REQUIRED"),
          business_dependency("OUTBOUND_CALL_ADAPTER", "外呼/APP证据接入方案未确认")],
         "LEAD_FIRST_CONTACT", [material("CONTACT_PROOF", 1, "通话记录、录音或人工补录凭证")],
         escalation("td001"),
         {"conditionalRequired": [{"when": {"field": "contactResult", "equals": "VALID"}, "field": "name"},
                                   {"when": {"field": "contactResult", "equals": "VALID"}, "field": "city"},
                                   {"when": {"field": "contactResult", "equals": "VALID"}, "field": "demand"},
                                   {"when": {"field": "contactResult", "equals": "VALID"}, "field": "visited"}]}),
    item("TD-002", "疑似无效主管复核", "阶段1-2", "LEAD", "LEAD_SUSPECT_INVALID_MARKED",
         supervisor("ownerId", "law_partner_manager"), ["reviewResult", "reviewOpinion"],
         fields(("reviewResult", "dict", "复核结果"), ("reviewOpinion", "textarea", "复核意见")),
         {"calendarCode": "DEFAULT", "minutes": 1440, "onDue": "DEFAULT_APPROVE"},
         route("TD-002", transition("TRUE_INVALID", "END", action="MOVE_DEAD_POOL"),
               transition("MISJUDGED_VALID", "TD-001", action="REOPEN_FIRST_CONTACT"),
               transition("DUE", "END", action="DEFAULT_APPROVE_INVALID")), [],
         [business_dependency("LEAD_INVALID_REVIEW_EVENT", "缺少疑似无效事件生产者"),
          business_dependency("LEAD_INVALID_REVIEW_HANDLER", "缺少Dead-Pool/重开首联业务处理器")],
         auto_actions=[{"config": {"ruleKey": "td002-default-approve", "actionType": "COMPLETE_DEFAULT",
                                    "capability": "COMPLETE_DEFAULT", "triggerAt": "DUE",
                                    "maxAttempts": 3, "retryDelayMinutes": 5,
                                    "claimTimeoutMinutes": 15}}]),
    item("TD-003", "无法联系重试", "阶段1-2", "LEAD", "LEAD_FIRST_CONTACT_UNREACHABLE",
         payload("ownerId", "sales"), ["attemptStage", "attemptCount", "contactResult"],
         fields(("attemptStage", "dict", "重试阶段"), ("attemptCount", "number", "拨号次数"),
                ("contactResult", "dict", "联系结果")),
         {"calendarCode": "DEFAULT", "schedule": ["T0", "T+1_AM_NOON_PM", "T+2_AM_NOON_PM"],
          "configuredBy": "Q-007"},
         route("TD-003", transition("CONNECTED", "TD-004", action="WRITE_LEAD_VALID"),
               transition("NEXT_WINDOW", "TD-003", action="SCHEDULE_NEXT_RETRY"),
               transition("EXHAUSTED", "END", action="TAG_AND_RETURN_POOL")), ["Q-007", "Q-008"],
         [business_dependency("LEAD_RETRY_EVENT", "缺少无法联系事件生产者"),
          business_dependency("LEAD_RETRY_HANDLER", "缺少T0/T+1/T+2状态及回公海处理器")],
         materials=[material("CONTACT_PROOF", 1, "每次拨号记录")]),
    item("TD-004", "5天实质进展待办", "阶段3", "CUSTOMER", "LEAD_FIRST_CONTACT_VALID",
         payload("ownerId", "sales"), ["progressType", "progressAt"],
         fields(("progressType", "dict", "实质进展类型"), ("progressAt", "datetime", "进展时间"),
                ("remark", "textarea", "进展说明")),
         {"calendarCode": "DEFAULT", "minutes": 7200, "thresholds": [80, 100, 150], "repeat": True},
         route("TD-004", transition("PROGRESS", "TD-004", action="REFRESH_FIVE_DAY_WINDOW"),
               transition("NO_PROGRESS_30_DAYS", "TD-005", action="MARK_NO_PROGRESS")), [],
         [business_dependency("CUSTOMER_PROGRESS_EVENT", "缺少首联有效到客户跟进事件"),
          business_dependency("CUSTOMER_PROGRESS_HANDLER", "缺少六类进展留痕和刷新周期处理器")],
         materials=[material("FOLLOWUP_PROOF", 1, "录音、截图、报价或外访凭证")],
         auto_actions=escalation("td004")),
    item("TD-005", "无进展/失联回公海处置", "阶段3", "CUSTOMER", "CUSTOMER_PROGRESS_STALE",
         supervisor("ownerId", "sales"), ["invalidLevel", "reason"],
         fields(("invalidLevel", "dict", "无效客户分级"), ("reason", "textarea", "处置原因")),
         {"calendarCode": "DEFAULT", "policyCode": "CUSTOMER_STALE_RETURN_POOL"},
         route("TD-005", transition("COMPLETE", "END", action="TAG_AND_RETURN_POOL")), ["Q-008"],
         [business_dependency("CUSTOMER_STALE_EVENT", "缺少30天无进展/失联事件生产者"),
          business_dependency("CUSTOMER_RETURN_POOL_HANDLER", "缺少客户分级并回公海处理器")],
         auto_actions=[{"config": {"ruleKey": "td005-return-pool", "actionType": "RETURN_POOL",
                                    "capability": "RETURN_POOL", "triggerAt": "DUE",
                                    "maxAttempts": 3, "retryDelayMinutes": 5,
                                    "claimTimeoutMinutes": 15}}]),
    item("TD-006", "执行案件折扣审批", "阶段3", "CONTRACT", "QUOTE_DISCOUNT_APPROVAL_REQUESTED",
         payload("approvalOwnerId", "law_partner_manager"),
         ["discountPercent", "discountReason", "approvalResult"],
         fields(("discountPercent", "number", "折扣比例"), ("discountReason", "textarea", "折扣理由"),
                ("approvalResult", "dict", "审批结果")),
         {"calendarCode": "WORKDAY", "policyCode": "EXECUTION_DISCOUNT_APPROVAL"},
         route("TD-006", transition("APPROVED", "TD-007", action="APPROVE_QUOTE"),
               transition("REJECTED", "TD-004", action="RETURN_TO_FOLLOWUP"),
               notes=["≤20%销售、≤40%组长、≤50%部长、>50%主任+合伙人"]), [],
         [business_dependency("QUOTE_DOMAIN", "仓库无独立报价/折扣审批表与页面"),
          business_dependency("QUOTE_APPROVAL_OWNER_ROUTING", "仓库未实现按折扣比例解析销售/组长/部长/主任+合伙人的审批Owner"),
          business_dependency("QUOTE_DISCOUNT_HANDLER", "缺少分层审批业务处理器")]),
    item("TD-007", "冲突初筛/豁免", "阶段3", "CONTRACT", "CONFLICT_SCREENING_FAILED",
         payload("partnerId", "law_partner_manager"), ["subjectType", "conflictResult"],
         fields(("subjectType", "dict", "冲突主体类型"),
                ("naturalPersonName", "text", "自然人姓名"), ("idCardNo", "text", "身份证号"),
                ("opposingPartyName", "text", "对方当事人"),
                ("enterpriseName", "text", "企业名称"),
                ("unifiedSocialCreditCode", "text", "统一社会信用代码"),
                ("conflictResult", "dict", "冲突初筛结果"), ("waiverResult", "dict", "豁免结果"),
                ("waiverReason", "textarea", "豁免理由"), ("waiverAt", "datetime", "豁免时间"),
                ("waiverSignature", "file", "豁免电子签")),
         {"calendarCode": "DEFAULT", "screeningMode": "REAL_TIME", "waiverPolicy": "CONFIGURED"},
         route("TD-007", transition("NO_CONFLICT", "TD-008"),
               transition("WAIVED", "TD-008", action="AUDIT_CONFLICT_WAIVER"),
               transition("REJECTED", "END", action="TERMINATE_REFUND_AND_REFER")), [],
         [business_dependency("CONFLICT_DOMAIN", "仓库无冲突主体、初筛、豁免及电子签数据模型"),
          business_dependency("CONFLICT_WAIVER_SIGNATURE_BINDING", "动态表单文件字段尚未绑定冲突豁免电子签业务材料"),
          business_dependency("CONFLICT_WAIVER_HANDLER", "缺少豁免与终止退款业务处理器")],
         extra_dod={"conditionalRequired": [
             {"when": {"field": "subjectType", "equals": "NATURAL_PERSON"}, "field": "naturalPersonName"},
             {"when": {"field": "subjectType", "equals": "NATURAL_PERSON"}, "field": "idCardNo"},
             {"when": {"field": "subjectType", "equals": "NATURAL_PERSON"}, "field": "opposingPartyName"},
             {"when": {"field": "subjectType", "equals": "ENTERPRISE"}, "field": "enterpriseName"},
             {"when": {"field": "subjectType", "equals": "ENTERPRISE"}, "field": "unifiedSocialCreditCode"},
             {"when": {"field": "conflictResult", "equals": "CONFLICT"}, "field": "waiverResult"},
             {"when": {"field": "waiverResult", "equals": "WAIVED"}, "field": "waiverReason"},
             {"when": {"field": "waiverResult", "equals": "WAIVED"}, "field": "waiverAt"},
             {"when": {"field": "waiverResult", "equals": "WAIVED"}, "field": "waiverSignature"}
         ]}),
    item("TD-008", "合同生成待办", "阶段4-5", "CONTRACT", "QUOTE_ACCEPTED_CONFLICT_CLEARED",
         payload("ownerId", "sales"), ["templateId", "contractFields", "supplementaryTerms"],
         fields(("templateId", "dict", "合同模板"), ("contractFields", "textarea", "合同字段"),
                ("supplementaryTerms", "textarea", "补充约定")),
         {"calendarCode": "WORKDAY", "minutes": 480},
         route("TD-008", transition("GENERATED", "TD-009", action="CREATE_CONTRACT_DRAFT")), [],
         [business_dependency("QUOTE_ACCEPTED_EVENT", "缺少报价接受且冲突通过事件"),
          business_dependency("CONTRACT_GENERATION_HANDLER", "现有合同模块无按模板自动合成处理器")]),
    item("TD-009", "合同审批待办", "阶段4-5", "CONTRACT", "CONTRACT_SUBMITTED",
         payload("partnerId", "law_partner_manager"), ["auditResult", "opinion", "riskReviewResult"],
         fields(("auditResult", "dict", "审批结果"), ("opinion", "textarea", "审批意见"),
                ("riskReviewResult", "dict", "纯风险特殊审")),
         {"calendarCode": "WORKDAY", "policyCode": "CONTRACT_APPROVAL"},
         route("TD-009", transition("APPROVED", "TD-010", action="APPROVE_CONTRACT"),
               transition("REJECTED", "TD-008", action="RETURN_CONTRACT_DRAFT")), [],
         [business_dependency("TD009_HANDLER_ALIAS", "现有CONTRACT_REVIEW处理器未绑定TD-009", "ADAPTER_REQUIRED"),
          business_dependency("PURE_RISK_REVIEW_FIELDS", "合同审批处理器未覆盖PRD纯风险特殊审字段", "ADAPTER_REQUIRED")],
         "CONTRACT_REVIEW"),
    item("TD-010", "签章办理待办", "阶段4-5", "CONTRACT", "CONTRACT_APPROVED",
         payload("ownerId", "sales"), ["signMode", "signDate"],
         fields(("signMode", "dict", "签章方式"), ("signDate", "date", "签署日期"),
                ("caseManagerId", "user", "案管协作人"), ("adminId", "user", "行政协作人"),
                ("paperArchiveRemark", "textarea", "纸质附卷说明")),
         {"calendarCode": "WORKDAY", "policyCode": "CONTRACT_SIGN"},
         route("TD-010", transition("OFFLINE_SIGNED", "TD-011", action="ARCHIVE_SIGNED_CONTRACT"),
               transition("ONLINE_SIGNED", "TD-011", action="ARCHIVE_SIGNED_CONTRACT"),
               notes=["销售负责发起；线下签章需案管/行政协作；线上签章依赖电子签适配器"]), [],
         [business_dependency("TD010_HANDLER_ALIAS", "现有CONTRACT_SIGN处理器未绑定TD-010", "ADAPTER_REQUIRED"),
          business_dependency("SIGN_COLLABORATION_ASSIGNMENT", "仓库未实现销售、案管、行政的签章协作分工与候选人分配"),
          business_dependency("ONLINE_SIGNATURE_ADAPTER", "线上签章流程及案管/行政协作未实现")],
         "CONTRACT_SIGN", [material("SIGNED_CONTRACT", 1, "签署合同电子档或扫描件")]),
    item("TD-011", "缴费/催收待办", "阶段4-5", "CONTRACT", "RECEIVABLE_DUE",
         payload("salesOwnerId", "finance_manager"), ["receivableType", "collectionResult", "amount"],
         fields(("receivableType", "dict", "应收类型"), ("collectionResult", "dict", "催收结果"),
                ("amount", "number", "应收金额"), ("nextCollectionAt", "datetime", "下次催收时间")),
         {"calendarCode": "DEFAULT", "timeWindowsDays": [7, 15], "nodePolicyRef": "Q-009"},
         route("TD-011", transition("PAID", "TD-012", action="CONFIRM_RECEIPT"),
               transition("UNPAID", "TD-011", action="SCHEDULE_COLLECTION")), ["Q-009"],
         [business_dependency("RECEIVABLE_DUE_EVENT", "缺少时间型/节点型统一应收事件"),
          business_dependency("COLLECTION_HANDLER", "现有PAYMENT_CONFIRM为财务确认收款，不等同销售催收处理器")]),
    item("TD-012", "财务开票待办", "阶段4-5", "CONTRACT", "PAYMENT_FULLY_RECEIVED",
         payload("financeOwnerId", "finance_manager"), ["invoiceAction", "invoiceNo", "invoiceDate"],
         fields(("invoiceAction", "dict", "开票动作"), ("invoiceNo", "text", "发票号码"),
                ("invoiceDate", "date", "开票日期")),
         {"calendarCode": "WORKDAY", "policyCode": "INVOICE_HANDLE"},
         route("TD-012", transition("INVOICED", "TD-013", action="WRITE_INVOICE")), [],
         [business_dependency("FULL_PAYMENT_EVENT", "现有PAYMENT_CONFIRMED不保证合同+基础/一次性费用全额到账"),
          business_dependency("TD012_HANDLER_ALIAS", "现有INVOICE_HANDLE处理器未绑定TD-012", "ADAPTER_REQUIRED")],
         "INVOICE_HANDLE"),
    item("TD-013", "转案材料补齐", "阶段4-5", "CONTRACT", "DEAL_CONFIRMED",
         payload("ownerId", "sales"), ["materialsConfirmed"],
         fields(("materialsConfirmed", "dict", "11项材料确认"), ("remark", "textarea", "补充说明")),
         {"calendarCode": "WORKDAY", "minutes": 960},
         route("TD-013", transition("COMPLETE", "TD-014", action="SUBMIT_CASE_HANDOFF")), [],
         [business_dependency("DEAL_CONFIRMED_EVENT", "缺少统一成交确认事件"),
          business_dependency("CASE_HANDOFF_MATERIAL_HANDLER", "缺少11项材料对象化校验和提交处理器")],
         materials=[material("SIGNED_CONTRACT"), material("PAYMENT_PROOF"), material("AUTHORIZATION"),
                    material("CLIENT_ID"), material("OPPOSING_PARTY_INFO"), material("EVIDENCE"),
                    material("CASE_REGISTRATION"), material("RISK_NOTICE"), material("LEGAL_AID_NOTICE"),
                    material("CONSULTATION_RECORD"), material("CASE_SCORE")]),
    item("TD-014", "案管接收待办", "阶段4-5", "CASE", "CASE_HANDOFF_SUBMITTED",
         payload("caseManagerId", "case_manager"), ["reviewResult", "reviewOpinion"],
         fields(("reviewResult", "dict", "材料核验结果"), ("reviewOpinion", "textarea", "退回/接收意见")),
         {"calendarCode": "WORKDAY", "minutes": 480},
         route("TD-014", transition("ACCEPTED", "TD-015", action="ACCEPT_CASE_HANDOFF"),
               transition("RETURNED", "TD-013", action="RETURN_CASE_HANDOFF")), [],
         [business_dependency("CASE_HANDOFF_EVENT", "现有CASE_TRANSFER_REQUESTED是律师转案，不是销售转案"),
          business_dependency("CASE_HANDOFF_REVIEW_HANDLER", "缺少销售转案材料接收/退回处理器")]),
    item("TD-015", "案管分类待办", "阶段4-5", "CASE", "CASE_HANDOFF_ACCEPTED",
         payload("caseManagerId", "case_manager"), ["businessLine"],
         fields(("businessLine", "dict", "业务线分类"), ("classificationOpinion", "textarea", "分类说明")),
         {"calendarCode": "WORKDAY", "minutes": 480},
         route("TD-015", transition("COMPREHENSIVE", "TD-016", action="CLASSIFY_CASE"),
               transition("NON_LITIGATION", "TD-017", action="CLASSIFY_CASE"),
               transition("ENFORCEMENT", "TD-019", action="CLASSIFY_CASE")), ["Q-001"],
         [business_dependency("CASE_CLASSIFICATION_FIELDS", "案件表无业务线分类及复核状态"),
          business_dependency("CASE_CLASSIFICATION_HANDLER", "缺少分类落库和三业务线路由处理器")]),
    item("TD-016", "综法分案待办", "阶段6", "CASE", "CASE_CLASSIFIED_COMPREHENSIVE",
         payload("partnerId", "law_partner_manager"),
         ["primaryLawyerId", "secondaryLawyerId", "assignmentAction"],
         fields(("primaryLawyerId", "user", "一级律师"), ("secondaryLawyerId", "user", "二级律师"),
                ("assignmentAction", "dict", "分案动作"), ("rejectReason", "dict", "拒接理由")),
         {"calendarCode": "WORKDAY", "minutes": 480},
         route("TD-016", transition("ACCEPTED", "MATTER_CLIENT_CONTACT", action="ASSIGN_CASE"),
               transition("REASSIGN", "TD-016", action="REASSIGN_CASE"),
               transition("REFUND", "END", action="TERMINATE_REFUND_AND_REFER")), ["Q-004", "Q-005"],
         [business_dependency("COMPREHENSIVE_CLASSIFIED_EVENT", "缺少综法分类完成事件"),
          business_dependency("HIERARCHICAL_ASSIGNMENT_HANDLER", "现有CASE_ASSIGN仅单层分案，未覆盖合伙人→一级→二级及拒接审核")],
         "CASE_ASSIGN"),
    item("TD-017", "非诉业务分案待办", "非诉流程", "NON_LITIGATION", "CASE_CLASSIFIED_NON_LITIGATION",
         payload("primaryLawyerId", "lawyer"), ["serviceTypes", "assigneeMode", "secondaryLawyerId"],
         fields(("serviceTypes", "dict", "非诉业务类型"), ("assigneeMode", "dict", "自办/分派"),
                ("secondaryLawyerId", "user", "二级律师")),
         {"calendarCode": "WORKDAY", "policyCode": "NON_LITIGATION_ASSIGNMENT"},
         route("TD-017", transition("ASSIGNED", "TD-018", action="CREATE_NON_LITIGATION_WORK")), [],
         [business_dependency("NON_LITIGATION_DOMAIN", "仓库无非诉事项、业务标签和办理状态表"),
          business_dependency("NON_LITIGATION_MULTI_SELECT_UI", "现有动态表单字典组件不支持非诉业务类型多选"),
          business_dependency("NON_LITIGATION_ASSIGN_HANDLER", "缺少自办/分派业务处理器")]),
    item("TD-018", "非诉成果交付待办", "非诉流程", "NON_LITIGATION", "NON_LITIGATION_WORK_COMPLETED",
         payload("assigneeId", "lawyer"), ["deliveryResult", "signedAt"],
         fields(("deliveryResult", "dict", "交付结果"), ("signedAt", "datetime", "签收时间"),
                ("deliveryRemark", "textarea", "交付说明")),
         {"calendarCode": "WORKDAY", "policyCode": "NON_LITIGATION_DELIVERY"},
         route("TD-018", transition("SIGNED", "END", action="CLOSE_AND_CALCULATE_PERFORMANCE")), ["Q-002"],
         [business_dependency("NON_LITIGATION_DOMAIN", "仓库无非诉成果、签收和直接结案表"),
          business_dependency("NON_LITIGATION_DELIVERY_HANDLER", "缺少成果交付、留存、结案和绩效处理器")],
         materials=[material("NON_LITIGATION_DELIVERABLE"), material("CLIENT_RECEIPT")]),
    item("TD-019", "执行接单待办", "执行流程", "ENFORCEMENT", "CASE_CLASSIFIED_ENFORCEMENT",
         payload("primaryAssistantId", "enforcement_primary_assistant"),
         ["acceptanceResult", "rejectReason"],
         fields(("acceptanceResult", "dict", "接单结果"), ("rejectReason", "dict", "拒接理由"),
                ("rejectOpinion", "textarea", "拒接说明")),
         {"calendarCode": "WORKDAY", "minutes": 480},
         route("TD-019", transition("ACCEPTED", "TD-020", action="ACCEPT_ENFORCEMENT_ORDER"),
               transition("REJECTED", "ENFORCEMENT_REJECTION_REVIEW", action="SUBMIT_REJECTION_REVIEW")), ["Q-003"],
         [business_dependency("ENFORCEMENT_ROLES", "仓库无一级助理/二级助理/执行部长角色映射"),
          business_dependency("ENFORCEMENT_DOMAIN", "仓库无执行接单、拒接审核和退款状态表"),
          business_dependency("ENFORCEMENT_ACCEPT_HANDLER", "缺少执行接单/拒接处理器")]),
    item("TD-020", "执行建联与强执判断", "执行流程", "ENFORCEMENT", "ENFORCEMENT_ORDER_ACCEPTED",
         payload("secondaryAssistantId", "enforcement_secondary_assistant"),
         ["contactedAt", "enforcementApplied"],
         fields(("contactedAt", "datetime", "建联时间"), ("enforcementApplied", "dict", "是否已申请强执"),
                ("filingMode", "dict", "未申请时提交方式")),
         {"calendarCode": "WORKDAY", "minutes": 480},
         route("TD-020", transition("ALREADY_APPLIED", "TD-021", action="ENTER_ENFORCEMENT_BUREAU_FLOW"),
               transition("NOT_APPLIED", "TD-021", action="PREPARE_AND_SUBMIT_ENFORCEMENT_APPLICATION")), ["Q-003"],
         [business_dependency("ENFORCEMENT_ROLES", "仓库无执行助理角色映射"),
          business_dependency("ENFORCEMENT_DOMAIN", "仓库无强执申请/执行局状态模型"),
          business_dependency("ENFORCEMENT_CONTACT_HANDLER", "缺少建联与强执判断处理器")],
         materials=[material("CONTACT_SCREENSHOT")]),
    item("TD-021", "执行服务节点待办", "执行流程", "ENFORCEMENT", "ENFORCEMENT_SERVICE_NODE_READY",
         payload("ownerId", "enforcement_secondary_assistant"), ["nodeType", "progressResult", "reportedAt"],
         fields(("nodeType", "dict", "执行节点类型"), ("progressResult", "textarea", "进展结果"),
                ("reportedAt", "datetime", "上报时间")),
         {"calendarCode": "WORKDAY", "nodeDurationsDays": [5, 15, 30], "repeat": True},
         route("TD-021", transition("NEXT_NODE", "TD-021", action="ADVANCE_ENFORCEMENT_NODE"),
               transition("CLOSE", "TD-025", action="SUBMIT_ENFORCEMENT_CLOSE_REVIEW")), ["Q-003"],
         [business_dependency("ENFORCEMENT_ROLES", "仓库无执行助理/律师角色映射"),
          business_dependency("ENFORCEMENT_DOMAIN", "仓库无执行服务分流、节点和周期上报表"),
          business_dependency("ENFORCEMENT_NODE_HANDLER", "缺少执行节点上报和循环处理器")],
         materials=[material("ENFORCEMENT_PROGRESS_PROOF")]),
    item("TD-022", "综法庭审笔录待办", "阶段7", "MATTER", "MATTER_HEARING_COMPLETED",
         payload("secondaryLawyerId", "lawyer"), ["hearingDate", "hearingSummary"],
         fields(("hearingDate", "date", "开庭日期"), ("hearingSummary", "textarea", "庭审摘要")),
         {"calendarCode": "WORKDAY", "policyCode": "HEARING_MINUTES_UPLOAD"},
         route("TD-022", transition("COMPLETE", "TD-023", action="WRITE_HEARING_MINUTES")), ["Q-010"],
         [business_dependency("HEARING_COMPLETED_EVENT", "现有MATTER_NODE_COMPLETED未形成明确开庭事件契约"),
          business_dependency("HEARING_MINUTES_HANDLER", "现有MATTER_DOCUMENT_SUPPLY未绑定TD-022且未控制后续门槛", "ADAPTER_REQUIRED")],
         "MATTER_DOCUMENT_SUPPLY", [material("HEARING_MINUTES")]),
    item("TD-023", "裁判文书30日跟进", "阶段7", "MATTER", "MATTER_HEARING_COMPLETED",
         payload("internLawyerId", "intern_lawyer"), ["followupResult", "followupAt"],
         fields(("followupResult", "dict", "跟进结果"), ("followupAt", "datetime", "跟进时间"),
                ("followupRemark", "textarea", "跟进说明")),
         {"calendarCode": "DEFAULT", "minutes": 43200, "repeat": True},
         route("TD-023", transition("RECEIVED", "MATTER_CLOSE_REVIEW", action="REGISTER_JUDGMENT"),
               transition("NOT_RECEIVED", "TD-023", action="SCHEDULE_NEXT_30_DAY_CYCLE")), ["Q-010", "Q-011"],
         [business_dependency("HEARING_COMPLETED_EVENT", "缺少开庭完成且未收文书事件契约"),
          business_dependency("JUDGMENT_FOLLOWUP_HANDLER", "缺少30日循环、文书登记和结案路由处理器")],
         materials=[material("JUDGMENT_FOLLOWUP_PROOF")]),
    item("TD-024", "风险代理费催收待办", "阶段7", "MATTER", "RISK_FEE_CONFIRMED",
         payload("salesOwnerId", "sales"), ["riskFeeAmount", "collectionResult"],
         fields(("riskFeeAmount", "number", "风险代理费"), ("collectionResult", "dict", "催收结果"),
                ("nextCollectionAt", "datetime", "下次催收时间")),
         {"calendarCode": "DEFAULT", "policyRef": "Q-009"},
         route("TD-024", transition("PAID", "MATTER_CLOSE_REVIEW", action="CONFIRM_RISK_FEE"),
               transition("UNPAID", "TD-024", action="SCHEDULE_RISK_FEE_COLLECTION")), ["Q-009", "Q-012"],
         [business_dependency("RISK_FEE_FIELDS", "仓库无风险代理识别、计算、审核和应收字段"),
          business_dependency("RISK_FEE_HANDLER", "缺少计算结果回转销售及催收处理器")]),
    item("TD-025", "归档交接/归档待办", "阶段7", "MATTER", "CASE_CLOSED",
         payload("assistantId", "case_manager"), ["handoffResult", "archiveNo"],
         fields(("handoffResult", "dict", "归档交接结果"), ("archiveNo", "text", "归档编号"),
                ("archiveOpinion", "textarea", "归档意见")),
         {"calendarCode": "WORKDAY", "phases": [{"code": "HANDOFF", "minutes": 9600},
                                                     {"code": "ARCHIVE", "minutes": 4800}]},
         route("TD-025", transition("HANDOFF_COMPLETE", "TD-025", action="ASSIGN_ARCHIVIST"),
               transition("ARCHIVED", "END", action="CALCULATE_PERFORMANCE")), [],
         [business_dependency("TD025_HANDLER_ALIAS", "现有CASE_ARCHIVE_CONFIRM只覆盖档案管理员确认，未覆盖20日交接阶段", "ADAPTER_REQUIRED"),
          business_dependency("LAWYER_ASSISTANT_ROLE_MAPPING", "仓库无律师助理稳定角色映射"),
          business_dependency("PERFORMANCE_DOMAIN", "仓库无归档完成后的绩效规则/结果表与处理器")],
         "CASE_ARCHIVE_CONFIRM", [material("ARCHIVE_HANDOFF_FORM"), material("ARCHIVE_SCAN")]),
]


def compact(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True)


def sql_text(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def generate_resources() -> None:
    RESOURCE_DIR.mkdir(parents=True, exist_ok=True)
    manifest = {
        "catalogVersion": "v0.2",
        "definitionSchemaVersion": 1,
        "source": "待办事项驱动律所管理系统_整体需求PRD_更新确认版_v0.2.docx",
        "templates": [{"templateCode": entry["templateCode"],
                        "resource": f"{entry['templateCode']}.json",
                        "foundationState": entry["foundationState"]}
                       for entry in TEMPLATES],
    }
    (RESOURCE_DIR / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    expected = {f"{entry['templateCode']}.json" for entry in TEMPLATES} | {"manifest.json"}
    for old in RESOURCE_DIR.glob("*.json"):
        if old.name not in expected:
            old.unlink()
    for entry in TEMPLATES:
        (RESOURCE_DIR / f"{entry['templateCode']}.json").write_text(
            json.dumps(entry, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def generate_matrix() -> None:
    lines = ["# v0.2 Foundation 待办模板准入矩阵", "",
             "> 仓库是运行能力的判定依据。`definitionPackageState=READY` 只表示机器可读草稿字段完整且可做结构校验，不表示运行时Owner、业务处理器或发布预检已经通过；",
             "> `foundationState/productionState=BLOCKED` 表示业务决策、事件生产者、处理器、角色或数据模型尚未形成投产闭环。", "",
             "| 编号 | 模板 | 阶段 | 对象 | 定义包 | Foundation/投产 | 决策 | 完成处理器 | 业务依赖/处理器缺口 | 验收引用 |",
             "|---|---|---|---|---|---|---|---|---|---|"]
    for entry in TEMPLATES:
        decisions = "、".join(entry["definition"]["decisionRefs"]) or "—"
        missing = "；".join(dep["code"] for dep in entry["businessDependencies"]
                           if dep["repositoryStatus"] != "PRESENT") or "—"
        state = f"{entry['foundationState']}/{entry['productionState']}"
        handler = (f"{entry['handlerCapability']['requiredCode']}/"
                   f"{entry['handlerCapability']['repositoryStatus']}")
        acceptance = "<br>".join(entry["acceptanceRefs"])
        lines.append(f"| {entry['templateCode']} | {entry['templateName']} | {entry['stage']} | "
                     f"{entry['businessType']} | {entry['definitionPackageState']} | {state} | {decisions} | "
                     f"{handler} | {missing} | {acceptance} |")
    ready = sum(entry["foundationState"] == "READY" for entry in TEMPLATES)
    lines += ["", "## 当前结论", "",
              f"- 定义包完整性：25/25 READY（仅静态结构，不代表发布准入）。", f"- 模板投产准入：{ready}/25 READY，{25-ready}/25 BLOCKED。",
              "- BLOCKED 草稿可进入配置中心评审；预检会如实报告未解决决策，只有解除阻断并完成可执行路由绑定后才可模拟或发布。",
              "- 路由计划使用稳定 TD 编码描述；只有依赖版本发布并绑定数据库 `templateVersionId` 后，才能生成可执行路由图。", ""]
    MATRIX.write_text("\n".join(lines), encoding="utf-8")


def generate_migration() -> None:
    lines = [
        "-- v0.2 PRD definition catalogue. All templates are imported as BLOCKED drafts.",
        "-- This migration never updates an existing published template version or enables a trigger rule.",
        "create table if not exists todo_prd_definition_catalog (",
        "  template_code varchar(64) not null,",
        "  template_name varchar(128) not null,",
        "  stage_code varchar(64) not null,",
        "  business_type varchar(32) not null,",
        "  definition_package_state varchar(16) not null,",
        "  foundation_state varchar(16) not null,",
        "  production_state varchar(16) not null,",
        "  definition_json json not null,",
        "  routing_plan_json json not null,",
        "  handler_capability_json json not null,",
        "  business_dependencies_json json not null,",
        "  blockers_json json not null,",
        "  acceptance_refs_json json not null,",
        "  create_time datetime not null default current_timestamp,",
        "  update_time datetime not null default current_timestamp on update current_timestamp,",
        "  primary key (template_code),",
        "  constraint chk_todo_prd_package_state check (definition_package_state in ('READY','BLOCKED')),",
        "  constraint chk_todo_prd_foundation_state check (foundation_state in ('READY','BLOCKED')),",
        "  constraint chk_todo_prd_production_state check (production_state in ('READY','BLOCKED'))",
        ") engine=innodb comment='v0.2 PRD todo definition catalogue';",
        "",
    ]
    for code, title in DECISIONS.items():
        lines += [
            "insert into todo_decision(decision_code,title,description,blocking,status,create_by)",
            f"select {sql_text(code)},{sql_text(title)},{sql_text('v0.2 PRD待确认问题')},'Y','OPEN','migration'",
            f"where not exists(select 1 from todo_decision where decision_code={sql_text(code)});",
        ]
    lines.append("")
    events: dict[tuple[str, str], None] = {}
    for entry in TEMPLATES:
        events[(entry["definition"]["event"]["eventType"], entry["businessType"])] = None
    schema = compact({"type": "object", "additionalProperties": True})
    for (event_type, business_type) in events:
        lines += [
            "insert into todo_event_catalog(event_type,payload_version,business_object_type,payload_schema_json,producer,status,create_by)",
            f"select {sql_text(event_type)},1,{sql_text(business_type)},{sql_text(schema)},'V0.2_PRD_CONTRACT','ACTIVE','migration'",
            f"where not exists(select 1 from todo_event_catalog where event_type={sql_text(event_type)} and payload_version=1);",
        ]
    lines += ["", "insert into todo_prd_definition_catalog(",
              "  template_code,template_name,stage_code,business_type,definition_package_state,foundation_state,production_state,",
              "  definition_json,routing_plan_json,handler_capability_json,business_dependencies_json,blockers_json,acceptance_refs_json)",
              "values"]
    rows = []
    for entry in TEMPLATES:
        values = [entry["templateCode"], entry["templateName"], entry["stage"], entry["businessType"],
                  entry["definitionPackageState"], entry["foundationState"], entry["productionState"],
                  compact(entry["definition"]), compact(entry["routingPlan"]),
                  compact(entry["handlerCapability"]), compact(entry["businessDependencies"]),
                  compact(entry["blockers"]), compact(entry["acceptanceRefs"])]
        rows.append("(" + ",".join(sql_text(value) for value in values) + ")")
    lines.append(",\n".join(rows))
    lines += [
        "on duplicate key update",
        "  template_name=values(template_name),stage_code=values(stage_code),business_type=values(business_type),",
        "  definition_package_state=values(definition_package_state),foundation_state=values(foundation_state),production_state=values(production_state),",
        "  definition_json=values(definition_json),routing_plan_json=values(routing_plan_json),",
        "  handler_capability_json=values(handler_capability_json),business_dependencies_json=values(business_dependencies_json),",
        "  blockers_json=values(blockers_json),acceptance_refs_json=values(acceptance_refs_json),update_time=current_timestamp;",
        "",
        "insert into todo_template(template_code,template_name,business_type,current_version,status,create_by)",
        "select c.template_code,c.template_name,c.business_type,0,'0','migration'",
        "from todo_prd_definition_catalog c",
        "where not exists(select 1 from todo_template t where t.template_code=c.template_code);",
        "",
        "insert into todo_template_version(template_id,version_no,status,source_version_id,owner_rule_json,dod_rule_json,",
        "  sla_rule_json,next_rule_json,ui_schema_json,definition_schema_version,definition_json,compiled_json,",
        "  definition_hash,validation_report_json,published_by,published_time)",
        "select t.template_id,",
        "  coalesce((select max(existing.version_no)+1 from todo_template_version existing where existing.template_id=t.template_id),1),'DRAFT',null,",
        "  json_extract(c.definition_json,'$.owner.config'),json_extract(c.definition_json,'$.dod.config'),",
        "  json_extract(c.definition_json,'$.sla.config'),json_extract(c.definition_json,'$.routing.config'),",
        "  json_extract(c.definition_json,'$.ui.config'),1,c.definition_json,null,null,null,null,null",
        "from todo_prd_definition_catalog c join todo_template t on t.template_code=c.template_code",
        "where not exists(select 1 from todo_template_version v where v.template_id=t.template_id",
        "  and cast(v.definition_json as char)=cast(c.definition_json as char));",
        "",
        "-- Deliberately no todo_trigger_rule insert: every catalogue item is BLOCKED for production.",
    ]
    MIGRATION.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    if len(TEMPLATES) != 25:
        raise SystemExit(f"Expected 25 templates, got {len(TEMPLATES)}")
    codes = [entry["templateCode"] for entry in TEMPLATES]
    expected = [f"TD-{index:03d}" for index in range(1, 26)]
    if codes != expected:
        raise SystemExit(f"Template codes are not exact/ordered: {codes}")
    generate_resources()
    generate_matrix()
    generate_migration()


if __name__ == "__main__":
    main()
