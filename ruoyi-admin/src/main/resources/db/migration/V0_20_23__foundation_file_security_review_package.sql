-- Review enablement only: the independent reviewer, sign-off and admission evidence remain unassigned and unapproved.
update todo_foundation_file_security_requirement
set source_ref='doc/reviews/v0.2-foundation-g05-file-security-review-package.md',
    remark='独立安全Reviewer须按评审包复核越权、令牌重放、访问审计、清理补偿、PRD材料E2E与残余风险；签字前保持NEEDS_REVIEW'
where gate_code='G-05'
  and requirement_code='SECURITY_REVIEW_SIGNOFF'
  and source_status='NEEDS_REVIEW';
