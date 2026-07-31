alter table todo_trigger_rule
  add column entry_slot_code varchar(64) null after business_type;

alter table todo_template
  add column replacement_template_code varchar(64) null after status;

update todo_trigger_rule r
join todo_template t on t.template_id=r.template_id
set r.entry_slot_code='LEAD_FIRST_CONTACT_ENTRY'
where r.event_type='LEAD_ASSIGNED'
  and t.template_code in ('LEAD_FIRST_CONTACT','TD-001');

update todo_trigger_rule
set enabled='N',update_by='flyway-v0.20.70',update_time=sysdate(),version=version+1
where entry_slot_code='LEAD_FIRST_CONTACT_ENTRY'
  and trigger_rule_id<>(select selected_id from (
    select r.trigger_rule_id selected_id
    from todo_trigger_rule r
    join todo_template t on t.template_id=r.template_id
    join todo_template_version v on v.version_id=r.template_version_id
    where r.entry_slot_code='LEAD_FIRST_CONTACT_ENTRY'
      and t.template_code='TD-001' and v.status='PUBLISHED'
    order by v.version_no desc,r.trigger_rule_id desc limit 1
  ) selected);

alter table todo_trigger_rule
  add column active_entry_slot_code varchar(64)
    generated always as (case when enabled='Y' then entry_slot_code else null end) stored,
  add unique key uk_todo_trigger_active_entry_slot(active_entry_slot_code);

update todo_template
set status='1',replacement_template_code='TD-001',update_by='flyway-v0.20.70',update_time=sysdate()
where template_code='LEAD_FIRST_CONTACT';
