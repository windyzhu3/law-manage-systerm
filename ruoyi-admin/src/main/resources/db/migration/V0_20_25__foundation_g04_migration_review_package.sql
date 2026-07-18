-- Review enablement only: no business default, schema change, historical update or approval is applied here.
update todo_foundation_migration_requirement
set source_ref='doc/reviews/v0.2-foundation-g04-historical-migration-review-package.md',
    remark='案管须生成无法分类清单及SHA-256；架构、案管、DBA独立评审前保持NEEDS_EVIDENCE'
where gate_code='G-04' and requirement_code='UNCLASSIFIED_CASE_EXCEPTION_LIST'
  and source_status='NEEDS_EVIDENCE';

update todo_foundation_migration_requirement
set source_ref='doc/reviews/v0.2-foundation-g04-historical-migration-review-package.md',
    remark='批次键、异常清单和不可变审计的幂等/断点续跑方案待独立评审；评审前保持NEEDS_EVIDENCE'
where gate_code='G-04' and requirement_code='BACKFILL_BATCH_IDEMPOTENCY'
  and source_status='NEEDS_EVIDENCE';

update todo_foundation_migration_requirement
set source_ref='doc/reviews/v0.2-foundation-g04-historical-migration-review-package.md',
    remark='数量、枚举、异常、审计与Todo版本引用校验SQL待DBA演练；评审前保持NEEDS_EVIDENCE'
where gate_code='G-04' and requirement_code='BACKFILL_VALIDATION_SQL'
  and source_status='NEEDS_EVIDENCE';

update todo_foundation_migration_requirement
set source_ref='doc/reviews/v0.2-foundation-g04-historical-migration-review-package.md',
    remark='只回滚迁移后未再变化行的保护方案待DBA演练；评审前保持NEEDS_EVIDENCE'
where gate_code='G-04' and requirement_code='BACKFILL_ROLLBACK_SQL'
  and source_status='NEEDS_EVIDENCE';
