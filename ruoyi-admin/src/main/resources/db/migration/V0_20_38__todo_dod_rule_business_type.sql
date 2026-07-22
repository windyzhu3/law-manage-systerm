alter table todo_dod_rule
  add column business_type varchar(32) not null default 'ALL' after rule_type,
  add key idx_todo_dod_rule_business (business_type,status,update_time,dod_rule_id);

update todo_dod_rule rule_item
join (
  select ref.ref_id_value dod_rule_id, min(template_item.business_type) business_type,
         count(distinct template_item.business_type) business_type_count
  from todo_template_draft_rule_ref ref
  join todo_template_version version_item on version_item.version_id=ref.version_id
  join todo_template template_item on template_item.template_id=version_item.template_id
  where ref.ref_type='DOD'
  group by ref.ref_id_value
) inferred on inferred.dod_rule_id=rule_item.dod_rule_id and inferred.business_type_count=1
set rule_item.business_type=inferred.business_type
where rule_item.business_type='ALL';
