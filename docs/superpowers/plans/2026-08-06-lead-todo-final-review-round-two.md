# Lead Todo Final Review Round Two Implementation Plan

> Scope: close the two remaining Important findings before the v0.2 lead Todo flow is accepted.

## 1. Candidate-version governed routing

- Extend the business outcome catalog API with the editable candidate version id.
- For `TD-004` + `SCHEDULE_SELF`, materialize and validate only the current editable `TD-004` version.
- Keep `NEXT_TEMPLATE` targets bound to the published target catalog.
- Pass the same candidate context through workbench resources and journey validation so save, preflight and simulation consume one rule.
- Add focused tests proving exact candidate acceptance and missing, stale, wrong, and published-version rejection.

## 2. Unified TD-004 completion lock order

- Add a handler preparation phase and a shared completion orchestrator used by normal, automatic, and force completion.
- TD004 preparation locks and revalidates the authoritative lead before any Todo status/audit mutation.
- Preserve existing non-TD004 behavior with a no-op preparation default.
- Keep business completion exactly once; preparation may validate and lock but cannot write facts, schedules, routing, or Todo state.
- Add unit ordering tests and external MySQL normal-vs-force / normal-vs-auto races.

## 3. Verification and delivery

- Run focused RED/GREEN tests first.
- Run complete backend tests and exact external MySQL verification with zero skips.
- Run frontend unit, lint/gates, and production build.
- Start a clean database and execute the two Chrome acceptance cases, including explicit absence of server blockers for the exact TD004 self target.
- Update tracked acceptance and final-fix reports, commit only owned files, and leave user artifacts untouched.
