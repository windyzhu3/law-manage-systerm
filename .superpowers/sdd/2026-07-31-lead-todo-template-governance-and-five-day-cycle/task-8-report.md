# Task 8 Report: Persist TD-004 Progress and Refresh the Five-Day Cycle

## Outcome

- Added the typed `LeadProgressCompleteCommand` and replaced the TD-004 no-op completion adapter with the production progress-cycle service.
- A valid TD-004 completion now persists one immutable substantive-progress fact and creates one `LEAD_PROGRESS_5D` schedule with the source Todo's template version.
- The progress fact key is `LEAD_PROGRESS:{sourceTodoId}` and the plan key is `LEAD_PROGRESS_5D:{leadId}:{sourceTodoId}`.
- The schedule contains exactly one relative `P5D` window starting at `progressAt`, due after 7,200 minutes, with one occurrence and one attempt.
- The progress fact, schedule plan/window and fact-to-plan link commit in one Spring transaction. Schedule creation and link failures roll back every new row.
- Replays validate the immutable lead, source Todo, progress content/time, template version, schedule purpose and window before returning the existing fact and plan. A historical matching fact with a null plan link is safely recovered under a locking read.
- Completion validates the exact TD-004/LEAD identity, current owner/admin access, owner continuity, active lead state, controlled progress dictionary value, future-time tolerance and a persisted `FOLLOWUP_PROOF` attachment.
- The handler routing result is exactly `PROGRESS_RECORDED` and includes `followupId`, `schedulePlanId`, `nextDueAt` and `replayed`.

## Persistence and Migration

- Added forward-only Flyway migration `V0_20_76__lead_progress_cycle.sql`. Version `0.20.75` belongs to Task 7 in this branch, so no historical migration was edited.
- Added nullable `progress_at`, `source_todo_id`, `schedule_plan_id` and `idempotency_key` columns to `biz_lead_followup`.
- Added a unique idempotency key and `(lead_id, progress_at)` lookup index while preserving every historical row unchanged.
- Added idempotent insert, current locking read and conditional plan-link mapper operations.
- Expanded the existing schedule-plan locking projection to expose all immutable fields needed for safe replay validation. This is backward compatible with existing consumers.
- Updated the latest-version assertion in `LeadTemplateConfigurationMySqlIT` from `0.20.75` to `0.20.76`.

## TDD Evidence

- RED: focused test compilation initially failed because `LeadProgressCompleteCommand`, `LeadProgressCycleService`, provenance accessors and mapper operations did not exist.
- GREEN: the focused service/handler suite passes 8 tests covering creation, fully linked replay, semantic conflicts, null-link recovery, command/Todo identity, authorization/state/dictionary/time/proof gates and link failure.
- RED hardening: a real MySQL `REPEATABLE_READ` race synchronized both callers after absent reads. The losing replay initially reported a false immutable-plan conflict because `selectSchedulePlanForUpdate` projected only part of the plan.
- GREEN hardening: the locking projection now contains the plan identity, source Todo, business identity, purpose/key, timezone, rule version, assignment snapshot and anchor timestamp. Both concurrent callers return the same fact/plan, exactly one reports replay, and no duplicate or error escapes.

## Verification

1. Focused unit gate:
   `mvn -pl ruoyi-system -am '-Dtest=LeadProgressCycleServiceTest,LeadProgressHandoffTodoHandlerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
   - Result: BUILD SUCCESS; 8 passed, 0 failed/error/skipped.
2. Real MySQL migration/runtime regression gate:
   `mvn -pl law-business,ruoyi-system,ruoyi-admin -am '-Dtest=LeadProgressCycleServiceTest,LeadProgressHandoffTodoHandlerTest,LeadProgressCycleMigrationTest,LeadProgressCycleRuntimeMySqlTest,LeadTodoTransactionRollbackTest,TodoSchedulePurposeMigrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
   - Result: BUILD SUCCESS; 17 passed, 0 failed/error/skipped.
   - Migration advanced an isolated schema from `0.20.75` to `0.20.76`, preserved its historical fingerprint, installed nullable columns/indexes, rejected duplicate non-null keys and executed zero migrations on the second run.
   - Runtime tests used actual Spring transactions, MyBatis mappers, `TodoScheduleService`, MySQL 8 and `REPEATABLE_READ`: one fact, one plan, one window and one link under concurrency; complete rollback on schedule and link failure.
3. Default full backend suite:
   `mvn -pl law-business,law-todo,ruoyi-system,ruoyi-admin -am test`
   - Result: BUILD SUCCESS across all ten reactor modules.
   - 1,468 tests, 0 failures, 0 errors, 32 environment-conditioned skips.
4. `git diff --check`: exit 0.

## Scope Notes

- No Task 9 work was performed.
- No migration at or below `V0_20_75` was changed.
- User-owned `.superpowers/sdd/task-7-report.md`, `ruoyi-ui/vue.config.js`, browser/runtime output and test-result artifacts were not staged or modified by Task 8.
- External MySQL tests require a disposable administrator connection from which each test creates an isolated schema; the MySQL system schema is not a valid direct migration target.
