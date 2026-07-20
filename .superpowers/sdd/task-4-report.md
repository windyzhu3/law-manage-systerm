# Task 4 Report: Reusable DoD Rule Management

## Status

Implemented Task 4's reusable completion-condition (DoD) rule management service and its focused tests. This report replaces the unrelated historic Task 4 report in full.

## Behavior and files

Created:

- `law-todo/src/main/java/com/law/todo/application/TodoDodRuleManagementService.java`
- `law-todo/src/test/java/com/law/todo/application/TodoDodRuleManagementServiceTest.java`

The service provides typed list/detail projections, optimistic save/update, copy, enable/disable, and read-only sample validation. All writes are transactional and use the existing durable `todo_definition_action` claim/lock/complete pattern with entity type `DOD_RULE`.

The action fingerprint includes action type, source entity, expected version, actor identity, and canonical request JSON. Claims occur before current dictionary/catalogue validation, so an exact completed retry returns the recorded entity ID after dictionaries or runtime validator registrations change. A changed reuse of the same action ID returns `TODO_DOD_RULE_ACTION_CONFLICT`.

Validation checks the enabled DoD-rule type and rule-status dictionaries through `TodoDictionaryValidationPort`, then checks every external validator ref against the actual injected runtime `TodoBusinessValidator` collection via `catalogCode()`. No duplicate validator catalogue was created. The sample result uses `TodoFormValidator.ValidationIssue` as its stable issue type and aggregates every missing required field and attachment rather than stopping at the first failure.

Copy/toggle/save mutate only reusable rule rows; they do not update draft-reference rows or published snapshots.

## RED evidence

Initial command (the quoted Surefire property is required in this reactor so upstream modules with no matching test do not conceal the target module):

```powershell
mvn -pl law-todo -am -Dtest=TodoDodRuleManagementServiceTest '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: `BUILD FAILURE`, exit code 1 at `law-todo:testCompile`, because `TodoDodRuleManagementService` and its `DodTestResult` did not exist. This was the expected feature-absent RED state.

## GREEN and regressions

Focused DoD GREEN after the minimal implementation:

```powershell
mvn -pl law-todo -am -Dtest=TodoDodRuleManagementServiceTest '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: `BUILD SUCCESS`; 11 tests, 0 failures/errors.

Fresh requested regression run after adding explicit copy/toggle replay coverage:

```powershell
mvn -pl law-todo -am '-Dtest=TodoDodRuleManagementServiceTest,TodoFormValidatorTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: `BUILD SUCCESS`; 22 tests, 0 failures/errors/skips:

- `TodoDodRuleManagementServiceTest`: 13 tests
- `TodoFormValidatorTest`: 9 tests

The compile output contains existing repository deprecation/unchecked notes in unrelated Todo tests; no Task 4 compilation error occurred.

## Self-review

- `git diff --check` was clean.
- Save/copy/toggle all claim and complete through the durable action ledger and check actor/source/fingerprint identity under lock.
- Exact save retry avoids insert/update and replays before dictionary/catalogue validation; changed request reuse is a stable action conflict.
- Copy and toggle each have dedicated replay tests proving no duplicate source read/write.
- Update/toggle use the mapper's optimistic-version conditional update and return stable version-conflict codes.
- The sample result reports both missing fields and missing attachments and treats blank strings as absent.
- The service only reads configuration mapper rows and writes the reusable DoD rule; it does not mutate published runtime snapshots or reference rows.

## Concerns and follow-up

- The current generic sample API has only `(payload, attachments, actor)`. `TodoBusinessValidator` needs a concrete `TodoInstance`, so the sample can validate configured validator references against the real runtime catalogue but cannot execute business-specific validators without a future explicit sample business context. Its `validatorIssues` list is therefore correctly empty for this generic sample contract.
- The required field/attachment capabilities have no separate runtime catalogue or dictionary type in the approved schema; this task preserves the permitted JSON values and validates the two seeded DoD dictionaries plus runtime validator codes. Adding a new field/attachment registry would be scope expansion.
- Real-MySQL transaction/concurrency verification remains a later integration concern; focused unit tests prove mapper interaction and replay behavior.
