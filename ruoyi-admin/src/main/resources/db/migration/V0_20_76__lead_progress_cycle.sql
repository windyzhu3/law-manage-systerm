-- Persist TD-004 progress provenance without changing any historical follow-up fact.
alter table biz_lead_followup
  add column progress_at datetime null after follow_result,
  add column source_todo_id bigint null after follow_user_id,
  add column schedule_plan_id bigint null after source_todo_id,
  add column idempotency_key varchar(192) null after schedule_plan_id,
  add unique key uk_biz_lead_followup_idempotency(idempotency_key),
  add key idx_biz_lead_followup_progress(lead_id,progress_at);
