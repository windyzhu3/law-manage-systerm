-- Preserve the exact assignment-policy decision that produced each immutable retry schedule.
alter table todo_schedule_plan
  add column assignment_policy_id bigint null after rule_version_id,
  add column assignment_policy_version int null after assignment_policy_id,
  add column assignment_policy_snapshot_source varchar(32) null
    after assignment_policy_version,
  add key idx_todo_schedule_plan_assignment_policy
    (assignment_policy_id,assignment_policy_version);

-- Existing plans predate policy snapshot persistence. Preserve that fact explicitly instead of
-- inventing a current policy identity for historical work.
update todo_schedule_plan
set assignment_policy_snapshot_source='LEGACY_PRE_0_20_49'
where assignment_policy_id is null and assignment_policy_version is null;

alter table todo_schedule_plan
  modify column assignment_policy_snapshot_source varchar(32) not null,
  add constraint chk_todo_schedule_plan_policy_snapshot
    check (
      (assignment_policy_snapshot_source='LEGACY_PRE_0_20_49'
        and assignment_policy_id is null and assignment_policy_version is null)
      or
      (assignment_policy_snapshot_source='RESOLVED_POLICY'
        and assignment_policy_id is not null and assignment_policy_version is not null
        and assignment_policy_version>=0)
    );

-- A call can be recorded without completing its TD-003 occurrence until the window limit is met.
alter table biz_lead_retry_record
  drop check chk_biz_lead_retry_result,
  add constraint chk_biz_lead_retry_result
    check (contact_result in
      ('CONTINUE_CURRENT_WINDOW','CONNECTED','NEXT_WINDOW','EXHAUSTED'));
