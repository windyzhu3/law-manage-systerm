-- Guided lead templates may have immutable published versions above their historical seed drafts.
-- Retire those superseded editable rows so the configuration journey cannot fall back to stale data.

create temporary table tmp_superseded_guided_lead_version (
  version_id bigint not null primary key
) engine=innodb;

insert into tmp_superseded_guided_lead_version(version_id)
select stale.version_id
from todo_template template
join todo_template_version stale on stale.template_id=template.template_id
where template.template_code in ('TD-001','TD-002','TD-003','TD-004')
  and stale.status in ('DRAFT','BLOCKED')
  and exists (
    select 1
    from todo_template_version published
    where published.template_id=stale.template_id
      and published.status='PUBLISHED'
      and published.version_no>stale.version_no
  );

update todo_trigger_rule rule_row
join tmp_superseded_guided_lead_version stale on stale.version_id=rule_row.template_version_id
set rule_row.enabled='N',rule_row.update_by='flyway-v0.20.79',
    rule_row.update_time=sysdate(),rule_row.version=rule_row.version+1
where rule_row.enabled='Y';

update todo_template_version version_row
join tmp_superseded_guided_lead_version stale on stale.version_id=version_row.version_id
set version_row.status='RETIRED',version_row.update_by='flyway-v0.20.79',
    version_row.update_time=sysdate();

drop temporary table tmp_superseded_guided_lead_version;
