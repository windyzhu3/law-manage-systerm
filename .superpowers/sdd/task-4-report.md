# Task 4 Report: Composable Owner Resolution

## Status

Complete. `law-todo` now exposes a composable typed owner resolver while preserving the existing `TodoAssignmentResolver.resolve(String, Map)` contract and every legacy `OWNER`, `USER`, `ROLE`, `DEPT`, `POST`, and `PAYLOAD` behavior.

## Implementation

- Added immutable `OwnerResolutionContext` and `OwnerResolutionResult` contracts.
- Added the public `OwnerStrategy` extension point and `CompositeOwnerResolver` registry.
- Implemented `USER`, `ROLE`, `DEPT`, `POST`, `PAYLOAD`, `BUSINESS_OWNER`, `SUPERVISOR`, `ROUND_ROBIN`, and `ASSIGNMENT_LEVEL`.
- Added deterministic null removal, de-duplication, ascending sorting, availability filtering, active delegation, nested candidate/cc rules, and fallback.
- Enforced nesting depth 8 and stable `TODO_OWNER_RULE_CYCLE` / `TODO_OWNER_RULE_DEPTH_EXCEEDED` failures.
- Rejected round-robin selections outside the supplied sorted available pool.
- Added `TodoOrganizationPort` as the only organization/availability boundary in `law-todo`; no RuoYi mapper is referenced by the resolver.
- Extended `TodoAssignmentResolver` with a typed facade overload and explicit port/composite constructors while retaining its no-argument Spring/legacy construction path.
- Kept Task 1 `operand` configurations executable in addition to the new config keys.

## TDD Evidence

RED:

- `mvn -pl law-todo -am '-Dtest=CompositeOwnerResolverTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  - Failed at test compilation because `CompositeOwnerResolver`, `OwnerResolutionContext`, `OwnerResolutionResult`, and `TodoOrganizationPort` did not exist.
- `CompositeOwnerResolverTest#roundRobinAlwaysExcludesUnavailableSourceUsers`
  - Failed with expected `[7]` but actual `[7, 8]` before the cursor-pool availability correction.
- `CompositeOwnerResolverTest#missingRequiredValueDoesNotCreateSyntheticZeroOwner`
  - Failed with expected `null` but actual `0` before missing operands were kept unresolved.

GREEN:

- Focused resolver suite: 18 tests, 0 failures, 0 errors.
- Affected resolver/event/routing suite: 35 tests, 0 failures, 0 errors.
- Full reactor through `law-todo`: 25 `law-business` tests plus 130 `law-todo` tests, 0 failures, 0 errors.

## Self-review

- Confirmed all nine required strategies are registered.
- Confirmed legacy facade outputs are unchanged for quoted and unquoted scalar rules.
- Confirmed cc users are never promoted, delegation runs only for an unavailable primary, and fallback runs only without an owner or usable candidates.
- Confirmed traces and result collections are deterministic and immutable.
- Confirmed `law-todo/assignment` and `TodoOrganizationPort` contain no RuoYi mapper or system-module dependency.
- Confirmed no frontend, database, or unrelated module files were changed.

## Concern

No concrete RuoYi `TodoOrganizationPort` adapter was added because no current runtime caller requires one to compile or preserve behavior. The Spring service therefore keeps the legacy-compatible default port; deployments using typed organization strategies must provide the approved adapter contract in a later integration task.

## Commit

`feat(todo): add composable owner resolution`
