# Task 4 Report: Auto-default fields and governed dictionary forms

## Status

Implemented and verified.

## RED evidence

Backend command:

```powershell
mvn -pl law-todo '-Dtest=TodoAutoActionConfigurationTest,TodoQueryServiceTest,TodoDodServiceTest,TodoFormValidatorTest' test
```

Result before production edits: `BUILD FAILURE` during `testCompile` after 11.674 seconds.
The five intended missing-contract errors were:

- `TodoFormOptionService` did not exist.
- `TodoConfigurationMapper.selectEnabledDictionaryData(String)` did not exist.
- `TodoQueryService` did not accept/use the option projection service.
- `TodoDodService` had no constructor accepting `TodoDictionaryValidationPort` (two test call sites).

Frontend command:

```powershell
node ruoyi-ui/scripts/check-todo-lead-dynamic-form.js
```

Result before production edits: assertion failure at the empty-dictionary renderer contract,
`true !== false`, proving `TodoDictField` still rendered `el-input`.

The existing COMPLETE_DEFAULT command builder already copied `config.fields`; the new test locked
that behavior before the remaining production work.

## GREEN evidence

Focused backend:

```text
Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 16.690 s
```

Frontend contracts:

```text
todo lead dynamic form contract passed
todo schema runtime contract passed
```

Backend regression:

```text
mvn -pl law-todo test
92 reports; 710 tests; 0 failures; 0 errors; 2 skipped
BUILD SUCCESS
```

Production frontend build:

```text
$env:NODE_OPTIONS='--openssl-legacy-provider'; npm run build:prod
DONE Build complete. The dist directory is ready to be deployed.
```

Encoding and diff checks:

```text
node ruoyi-ui/scripts/check-source-encoding.js
All frontend source files are valid UTF-8.

git diff --check
No whitespace errors (only existing line-ending conversion warnings).
```

## API and data flow

1. `TodoQueryService.form` decodes the immutable published definition.
2. `TodoFormOptionService` creates a new `UiSchema`, preserving the definition snapshot.
3. Each UI field declaring `dictType` is projected with current `{label,value}` options.
4. `TodoConfigurationMapper.selectEnabledDictionaryData` joins `sys_dict_type` and
   `sys_dict_data`, filters both statuses to enabled (`'0'`), and uses stable dictionary order.
5. Submission first passes persistence-independent `TodoFormValidator` required/conditional/material
   rules.
6. `TodoDodService` then checks each submitted declared dictionary field through
   `TodoDictionaryValidationPort`; disabled, missing, or unavailable values fail closed with
   `TODO_DOD_DICTIONARY_VALUE_INVALID`.
7. COMPLETE_DEFAULT continues through `TodoCommandService.autoComplete`, the normal COMPLETE DoD
   path, business completion/routing handlers, and the existing `COMPLETE_DEFAULT` audit action.

## Frontend behavior

- `normalizeFields(formView, state)` evaluates each field's `showWhen` against current values.
- `TodoDynamicForm` renders only fields whose effective visibility is not false.
- Hidden UI-only required fields are not validated.
- Effective DoD required fields remain authoritative even when the UI hides them.
- Active conditional DoD branches mark their target fields required.
- A visible dictionary field with no options renders an error alert, never a text input.
- The same missing-options state makes `validate()` return false.

## Changed files

- `law-todo/src/main/java/com/law/todo/application/TodoFormOptionService.java`
- `law-todo/src/main/java/com/law/todo/application/TodoQueryService.java`
- `law-todo/src/main/java/com/law/todo/application/view/TodoFormView.java`
- `law-todo/src/main/java/com/law/todo/application/TodoDodService.java`
- `law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java`
- `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml`
- `law-todo/src/test/java/com/law/todo/application/TodoAutoActionConfigurationTest.java`
- `law-todo/src/test/java/com/law/todo/application/TodoQueryServiceTest.java`
- `law-todo/src/test/java/com/law/todo/application/TodoDodServiceTest.java`
- `law-todo/src/test/java/com/law/todo/definition/TodoFormValidatorTest.java`
- `ruoyi-ui/src/components/TodoDynamicForm/index.vue`
- `ruoyi-ui/src/components/TodoDynamicForm/schema-runtime.js`
- `ruoyi-ui/scripts/check-todo-lead-dynamic-form.js`

No production edit was needed in `TodoAutoActionConfiguration` because it already copied
`config.fields` into `ActionCommand`. No production edit was needed in `TodoFormValidator`; it
already ignored UI-only required flags and enforced only effective DoD rules, preserving its
persistence independence.

## Self-review

- Published definition maps are never mutated; projection returns a new immutable `UiSchema`.
- Duplicate dictionary types are resolved once per form projection.
- Dictionary lookup is parameterized and fail-closed.
- Required/material validation precedes dictionary storage lookup; a test verifies the port is not
  called when a required field is missing.
- Existing 2-argument and 3-argument `TodoQueryService` constructors, plus existing
  `TodoDodService` constructors, remain compatible for tests and legacy callers.
- Schedule, assignment, routing, completion-handler, event, and audit behavior was not altered.
- User-owned Task 7 report, `ruoyi-ui/vue.config.js`, runtime logs, Playwright output, and test output
  were preserved and excluded from staging.

## Concerns

No blocking concerns. Historical published forms that declare `type: "dict"` without a `dictType`
will intentionally fail closed rather than degrade to free text; the definition/data migration task
must provide governed `dictType` values before those forms are actionable.
