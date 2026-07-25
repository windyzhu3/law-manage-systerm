-- Validate the two policy-snapshot shapes before changing an already-published 0.20.49 schema.
-- A partial identity or a non-positive ID/negative version aborts this forward migration.
create temporary table tmp_todo_schedule_policy_provenance_guard(
  valid_snapshot tinyint not null,
  constraint chk_tmp_todo_schedule_policy_provenance check (valid_snapshot=1)
);

insert into tmp_todo_schedule_policy_provenance_guard(valid_snapshot)
select case when count(*)=0 then 1 else 0 end
from todo_schedule_plan
where not (
  (assignment_policy_id is null and assignment_policy_version is null)
  or
  (assignment_policy_id is not null and assignment_policy_id>0
    and assignment_policy_version is not null and assignment_policy_version>=0)
);

drop temporary table tmp_todo_schedule_policy_provenance_guard;

alter table todo_schedule_plan
  add column assignment_policy_snapshot_source varchar(32) null
    after assignment_policy_version;

-- Plans that existed before policy identity persistence remain explicitly historical.
update todo_schedule_plan
set assignment_policy_snapshot_source='LEGACY_PRE_0_20_49'
where assignment_policy_id is null and assignment_policy_version is null;

-- Plans created by the published 0.20.49 runtime already contain the resolved immutable identity.
update todo_schedule_plan
set assignment_policy_snapshot_source='RESOLVED_POLICY'
where assignment_policy_id is not null and assignment_policy_id>0
  and assignment_policy_version is not null and assignment_policy_version>=0;

alter table todo_schedule_plan
  modify column assignment_policy_snapshot_source varchar(32) not null,
  add constraint chk_todo_schedule_plan_policy_snapshot
    check (
      (assignment_policy_snapshot_source='LEGACY_PRE_0_20_49'
        and assignment_policy_id is null and assignment_policy_version is null)
      or
      (assignment_policy_snapshot_source='RESOLVED_POLICY'
        and assignment_policy_id is not null and assignment_policy_id>0
        and assignment_policy_version is not null and assignment_policy_version>=0)
    );
