-- Preserve the exact assignment-policy decision that produced each immutable retry schedule.
alter table todo_schedule_plan
  add column assignment_policy_id bigint null after rule_version_id,
  add column assignment_policy_version int null after assignment_policy_id,
  add key idx_todo_schedule_plan_assignment_policy
    (assignment_policy_id,assignment_policy_version);

-- A call can be recorded without completing its TD-003 occurrence until the window limit is met.
alter table biz_lead_retry_record
  drop check chk_biz_lead_retry_result,
  add constraint chk_biz_lead_retry_result
    check (contact_result in
      ('CONTINUE_CURRENT_WINDOW','CONNECTED','NEXT_WINDOW','EXHAUSTED'));
