set @lead_entry_target=(
  select r.trigger_rule_id
  from todo_trigger_rule r
  join todo_template t on t.template_id=r.template_id
  join todo_template_version v on v.version_id=r.template_version_id
  where r.event_type='LEAD_ASSIGNED' and r.business_type='LEAD'
    and t.template_code='TD-001' and t.status='0' and v.status='PUBLISHED'
  order by v.version_no desc,r.trigger_rule_id desc limit 1
);

create temporary table todo_lead_ingress_target_v02071(
  trigger_rule_id bigint not null,
  constraint ck_todo_lead_ingress_target_v02071 check (trigger_rule_id>0)
);

insert into todo_lead_ingress_target_v02071(trigger_rule_id)
values(coalesce(@lead_entry_target,0));

update todo_trigger_rule
set enabled='N',entry_slot_code=null,update_by='flyway-v0.20.71',update_time=sysdate(),version=version+1
where entry_slot_code='LEAD_FIRST_CONTACT_ENTRY'
   or (event_type='LEAD_ASSIGNED' and business_type='LEAD');

update todo_trigger_rule r
join todo_lead_ingress_target_v02071 target on target.trigger_rule_id=r.trigger_rule_id
set r.enabled='Y',r.entry_slot_code='LEAD_FIRST_CONTACT_ENTRY',
    r.update_by='flyway-v0.20.71',r.update_time=sysdate(),r.version=r.version+1;

alter table todo_trigger_rule
  add constraint ck_todo_trigger_lead_ingress_slot
  check (event_type!='LEAD_ASSIGNED' or business_type!='LEAD' or enabled!='Y'
    or entry_slot_code='LEAD_FIRST_CONTACT_ENTRY');

drop temporary table todo_lead_ingress_target_v02071;
