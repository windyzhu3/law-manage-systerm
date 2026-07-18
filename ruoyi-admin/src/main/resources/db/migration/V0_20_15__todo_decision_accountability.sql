alter table todo_decision
  add column owner_user_id bigint null after resolution,
  add column owner_role_key varchar(64) null after owner_user_id,
  add column due_at datetime null after owner_role_key,
  add column delivery_phase varchar(16) not null default 'CROSS_PHASE' after due_at,
  add key idx_todo_decision_accountability (blocking,status,delivery_phase,due_at,owner_user_id),
  add constraint chk_todo_decision_delivery_phase
    check (delivery_phase in ('PHASE_ONE','PHASE_TWO','CROSS_PHASE'));

-- Phase allocation is copied from the approved two-stage Foundation plan.
-- Ownership, deadline and business conclusion intentionally remain unassigned.
update todo_decision
set delivery_phase='PHASE_ONE'
where decision_code in ('Q-001','Q-004','Q-005','Q-007','Q-008','Q-010','Q-011','Q-012');

update todo_decision
set delivery_phase='PHASE_TWO'
where decision_code in ('Q-002','Q-003','Q-006','Q-009');
