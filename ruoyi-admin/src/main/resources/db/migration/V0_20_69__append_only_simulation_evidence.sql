-- Every simulation execution is immutable audit evidence. A retry with the
-- same input must append a new result so a previous failure cannot permanently
-- block publication after the engine or referenced resources are repaired.
alter table todo_simulation_evidence
  drop index uk_todo_simulation_evidence;

create index idx_todo_simulation_evidence_input
  on todo_simulation_evidence(
    version_id, definition_hash, scenario_code, scenario_version, input_hash
  );
