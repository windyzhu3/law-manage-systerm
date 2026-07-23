# Todo Engine Phase 2 Design QA

Date: 2026-07-24

Reference: `docs/superpowers/specs/assets/2026-07-23-todo-engine-phase-two-ux-selected.png`

## Same-state evidence

The final captures and the approved reference all show the fourth journey step, `完成标准`, with the editor, employee preview, and bottom action bar visible.

- [1440×1024 comparison](../../../output/playwright/todo-phase-two-ux/1440x1024-comparison.png)
- [1920×1080 comparison](../../../output/playwright/todo-phase-two-ux/1920x1080-comparison.png)

The composition script preserves each source aspect ratio, labels both halves, and fails for missing or zero-sized inputs.

## Closed findings

| Severity | Finding | Correction | Verification |
|---|---|---|---|
| P1 | Initial comparison used Owner while the reference used DoD. | Capture is pinned to `step=DOD` and waits for `.dod-step`. | Both final comparisons show the fourth step. |
| P1 | A legacy payload owner rule did not hydrate its field, leaving a placeholder. | Centralized legacy owner-field selection and covered it in the journey model contract. | Owner field is populated; model suite 42/42. |
| P1 | Employee owner fallback was English. | Localized the server preview projection. | Both viewports show Chinese owner copy. |
| P1 | The 1440 right rail was too narrow and wrapped severely. | Changed the journey grid to a minimum 420 px / 32% preview column. | Employee preview remains readable at 1440 and 1920. |
| P1 | Optional DoD content was hidden behind the fixed footer. | Compacted recipe cards and grouped condition, instruction, and validator controls into a collapsed advanced section. | The complete normal configuration and advanced row sit above the footer in both captures. |
| P1 | DoD recipes never matched real event types or templates without a stage. | Added V0.20.45 to align the five recipes with real event types and wildcard stages. | The final LEAD capture shows one contextual recipe; real Flyway test passes. |
| P1 | E2E fixture titles displayed a `?` replacement marker. | Replaced middle-dot separators with ASCII ` - ` and added browser/capture assertions for `?` and U+FFFD. | Page title and employee preview have no replacement marker. |

## Final inspection

- Layout and hierarchy: PASS
- Spacing and responsive rail width: PASS
- Typography and Chinese copy: PASS
- Borders, radii, colors, and disabled controls: PASS
- Overflow, cropping, and fixed-footer clearance: PASS
- Same-state comparison validity: PASS
- Console and page errors during capture: none
- Open P0/P1/P2 findings: 0

Intentional differences from the illustrative reference are the repository application shell, deterministic E2E fixture copy, and the governed recipe/config-card interaction. They do not reduce task completion, readability, or workflow safety.

final result: passed
