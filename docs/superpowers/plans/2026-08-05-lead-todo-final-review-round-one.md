# Lead Todo Final Review Round One Implementation Plan

**Goal:** Close the four Important findings from the cumulative branch review without weakening the existing lead Todo release, simulation, or runtime guarantees.

**Approach:** Add adversarial tests before each production change. Keep release validation centralized in `LeadTodoReleaseService`, keep governed routing metadata deterministic in the journey model, and serialize TD-004 completion on the authoritative lead row before any authorization, state, fact, or schedule decision.

## Task 1: Reject inactive templates during release

**Files:**
- Modify: `law-todo/src/test/java/com/law/todo/application/LeadTodoReleaseServiceTest.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/LeadTodoReleaseService.java`
- Modify: the existing lead release external-MySQL integration test

1. Add unit tests proving readiness and activation reject any selected template whose `template_status` is not `0`.
2. Run the focused unit test and retain the failing result.
3. Enforce active-template validation in the shared release-version validator.
4. Add an external-MySQL regression covering inactive TD-002/TD-003/TD-004 and successful reactivation.
5. Run the focused unit and external tests to green.

## Task 2: Make the TD-002 graph match runtime semantics exactly

**Files:**
- Modify: `law-todo/src/test/java/com/law/todo/application/LeadTodoReleaseServiceTest.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/LeadTodoReleaseService.java`

1. Add adversarial tests for a higher-priority direct TD-002 edge and an extra decision branch.
2. Run the focused tests and retain the failures.
3. Require one TD-002 outgoing edge to one decision node.
4. Require exactly the `TRUE_INVALID -> END`, `MISJUDGED_VALID -> current TD-001`, and default `-> END` decision branches, with no extras.
5. Re-run focused release tests.

## Task 3: Preserve governed hidden target versions in the TD-004 UI

**Files:**
- Modify: `ruoyi-ui/scripts/check-todo-journey-model.js`
- Modify: `ruoyi-ui/src/views/todo/config/journey/journey-step-model.js`
- Modify: the existing Todo journey Chrome specification if its expectation reflects the obsolete null target

1. Add model tests proving `SCHEDULE_SELF` resolves and preserves the current published TD-004 numeric version.
2. Add blocker tests for a missing or wrong hidden governed target.
3. Run the model suite and retain the failing result.
4. Generalize outcome materialization and route blockers to governed outcomes with `targetTemplateCode`.
5. Run all frontend model/unit checks and production build.

## Task 4: Close the TD-004 completion TOCTOU window

**Files:**
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizLeadMapper.java`
- Modify: `ruoyi-system/src/main/resources/mapper/system/BizLeadMapper.xml`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadProgressCycleService.java`
- Modify: `ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadProgressCycleServiceTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/LeadProgressCycleRuntimeMySqlTest.java`

1. Add a mapper contract test for `SELECT ... FOR UPDATE` and update unit expectations to require the locked read.
2. Add real two-transaction tests where reassignment or pool transition wins before completion obtains the lead lock.
3. Demonstrate that current code either lacks the lock contract or creates stale work.
4. Lock and re-read the lead before authorization, state checks, policy selection, fact writes, or schedule writes; document the global lock order.
5. Prove the competing transaction blocks while holding the row and that completion creates no fact, schedule, or next Todo after revalidation, without deadlock.

## Task 5: Verify, report, and commit

1. Run focused backend tests, the complete backend suite, exact external-MySQL tests with zero skips, all frontend checks/build, and fresh Chrome 2/2 acceptance.
2. Tear down services and temporary database resources.
3. Review `git diff` and `git status`, excluding all pre-existing dirty artifacts.
4. Update the tracked acceptance report and plan-specific Task 11 report with exact heads, commands, exit codes, and test totals.
5. Commit only intended changes and report the final commit hash.
