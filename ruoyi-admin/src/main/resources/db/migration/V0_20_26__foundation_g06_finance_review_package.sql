-- Review enablement only: no finance policy, schema, fact, decision or approval is changed here.
update todo_foundation_finance_requirement
set source_ref='doc/reviews/v0.2-foundation-g06-finance-formula-review-package.md',
    remark='Q-009/Q-012、公式版本、金额精度、舍入、税费、退款、重算和催收Owner须由财务/业务/架构独立评审；签字前保持NEEDS_REVIEW'
where gate_code='G-06' and requirement_code='FINANCE_BUSINESS_SIGNOFF'
  and source_status='NEEDS_REVIEW';
