# Task 4 Report: Transactional, Idempotent Provisioning

## Status

Implemented Task 4 on `v0.2-Foundation`. The mapper interface follows the approved scan-boundary correction in commit `a2025aac`: it is located in `com.ruoyi.system.mapper`, while the catalog, result, and provisioning service remain in `com.ruoyi.system.foundation`.

No schema migration, runner, configuration property, controller, or UI was added. Real-MySQL transaction verification remains owned by Task 7.

## TDD evidence

### Initial RED

Command (PowerShell requires quoting both `-D` arguments):

```powershell
mvn -pl ruoyi-system -am "-Dtest=FoundationTestIdentityMapperContractTest,FoundationTestIdentityProvisioningServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD FAILURE`, exit code 1 during `ruoyi-system:testCompile`.

Expected failures included:

- missing `FoundationTestIdentityMapper`;
- missing `FoundationTestIdentityProvisioningService`;
- missing `FoundationTestIdentityProvisioningResult`;
- missing `SysUser#setUserType`.

This confirmed that the tests failed because Task 4 behavior and contracts did not exist.

### Wiring RED

After the minimal implementation compiled, the focused suite ran 12 tests and failed exactly one assertion because `FoundationTestPasswordPolicy` was not registered as a Spring component. The component assertion was observed RED before adding `@Component`.

### Self-review RED

Two regression tests were then added from self-review findings. The focused suite ran 13 tests and failed exactly two expected assertions:

- the permission query filtered `m.status`, which could hide a stored extra button grant from exact-set validation;
- an invalid reserved child department could be rejected only after attempting to insert its missing root.

The fixes removed the menu-status filter and moved reusable department tree validation into the read-only preflight phase.

### Independent-review hardening RED

The read-only review identified two additional in-scope edge cases. New regression checks produced a focused RED run with 14 tests and two expected failures:

- `LIMIT 1` ordering could select a valid marked row while hiding a real duplicate username, and same-name department lookup could hide a second collision row;
- a marked, non-deleted but disabled test user was not rejected before writes.

At that review stage, username collisions were surfaced with marker-aware SQL ordering and same-name department collisions with an aggregate sentinel. The later official-review correction documented below superseded marker-aware SQL: the mapper now returns every username row and the service classifies all rows with exact Java comparisons. The department duplicate sentinel remains and retains `dept_name` so MyBatis always materializes the conflict object.

### Focused GREEN

Command:

```powershell
mvn -pl ruoyi-system -am "-Dtest=FoundationTestIdentityMapperContractTest,FoundationTestIdentityProvisioningServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`, exit code 0.

```text
FoundationTestIdentityMapperContractTest: 3 tests, 0 failures, 0 errors
FoundationTestIdentityProvisioningServiceTest: 11 tests, 0 failures, 0 errors
Total: 14 tests, 0 failures, 0 errors, 0 skipped
```

### Fresh broader GREEN

Command:

```powershell
mvn -pl ruoyi-system -am "-Dtest=FoundationTestIdentityMapperContractTest,FoundationTestIdentityProvisioningServiceTest,FoundationTestIdentityCatalogTest,FoundationTestPasswordPolicyTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`, exit code 0.

```text
FoundationTestIdentityCatalogTest: 3 tests
FoundationTestIdentityMapperContractTest: 3 tests
FoundationTestIdentityProvisioningServiceTest: 11 tests
FoundationTestPasswordPolicyTest: 4 tests
Total: 21 tests, 0 failures, 0 errors, 0 skipped
```

The reactor compiled all affected main and test sources. The only emitted compiler note was the repository's pre-existing unchecked-operation note for `LeadAssignmentServiceTest`; Task 4 introduced no compilation error or warning.

## Exact files

Created:

- `ruoyi-system/src/main/java/com/ruoyi/system/mapper/FoundationTestIdentityMapper.java`
- `ruoyi-system/src/main/resources/mapper/system/FoundationTestIdentityMapper.xml`
- `ruoyi-system/src/main/java/com/ruoyi/system/foundation/FoundationTestIdentityProvisioningResult.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/foundation/FoundationTestIdentityProvisioningService.java`
- `ruoyi-system/src/test/java/com/ruoyi/system/foundation/FoundationTestIdentityMapperContractTest.java`
- `ruoyi-system/src/test/java/com/ruoyi/system/foundation/FoundationTestIdentityProvisioningServiceTest.java`

Modified:

- `ruoyi-common/src/main/java/com/ruoyi/common/core/domain/entity/SysUser.java`
- `ruoyi-system/src/main/resources/mapper/system/SysUserMapper.xml`
- `ruoyi-system/src/main/java/com/ruoyi/system/foundation/FoundationTestIdentityCatalog.java`
- `ruoyi-system/src/main/java/com/ruoyi/system/foundation/FoundationTestPasswordPolicy.java`
- `.superpowers/sdd/task-4-report.md`

The catalog now owns the five governance permission sets, preventing the service from duplicating Task 2 role/permission literals. The password policy is a Spring component so the provisioning service can inject and reuse it.

## Transaction and fail-before-write evidence

- `FoundationTestIdentityProvisioningService#provision(String)` is the single public provisioning operation and is annotated `@Transactional(rollbackFor = Exception.class)`.
- Execution order is password policy, 11-role preflight, Q-003 boundary validation, department/user conflict preflight, parent-first department materialization, and user/role materialization.
- All stable validation conflicts happen in read-only preflight before the first insert/update/delete.
- Tests verify role missing/mismatch, real username collision (including a deleted real username), unmarked same-name department, and invalid reserved tree all produce stable `FoundationTestIdentityException` codes without any write interaction.
- The dedicated mapper exposes no role creation or role-menu mutation method. Its XML has no `${}` substitution.

## Idempotency and repair evidence

- First run creates exactly 6 departments, 12 users, and 12 user-role links; generated keys are required before child/user relation work continues.
- Clean second run performs no department insert, user insert, placement update, role-link delete, or role-link insert and reports 30 reused records.
- Existing active marked users are reused only when `userType=99`, the remark starts with `USER_MARKER`, `createBy=foundation-test-seeder`, and `status=0`.
- A logically deleted marked test user is recreated as a new row; the deleted row is never overwritten or repaired.
- A marked active user's incorrect `dept_id` and role links are repaired to the exact target department and exactly one target role. No real user is mutated.
- Reusable departments require the exact reserved code, exact name, `create_by=foundation-test-seeder`, active/non-deleted state, and exact parent/ancestor structure. Same-name other-code rows and reserved codes owned by other creators fail with `DEPARTMENT_CONFLICT`.

## Password and sensitivity evidence

- Password policy validation runs before role/database reads that can lead to writes.
- `BCryptPasswordEncoder.encode` is called exactly once per newly inserted user (12 calls on a fresh run).
- A clean second run and repair run call the encoder zero times.
- Reuse and repair never write or reset the password column.
- `FoundationTestIdentityProvisioningResult` is a record with exactly `created`, `reused`, and `repaired` integer components; it contains no password, hash, username, or connection data.

## `user_type` mapper evidence

- `SysUser` now has a `userType` field with getter/setter.
- `SysUserResult`, `selectUserVo`, `selectUserList`, allocated list, and unallocated list read `user_type`.
- `insertUser` writes `user_type` when supplied.
- Ordinary `updateUser` does not contain or mutate `user_type`.
- The dedicated all-state username query reads `user_type`, marker, creator, status, and delete flag without a logical-delete filter, `LIMIT`, ordering, or SQL marker classification.
- The service examines every returned row, so real/unmarked, disabled, unknown-delete-state, or duplicate active rows cannot be hidden by another valid marked row.

## Self-review

Checked:

- `git diff --check`: clean after formatting fixes;
- mapper interface has exactly the 11 approved methods;
- dedicated XML uses parameter binding only;
- no `sys_role` insert and no `sys_role_menu` mutation;
- department insert does not reference a nonexistent `remark` column;
- governance comparison uses the exact button-permission set and does not hide disabled stored grants;
- duplicate usernames and duplicate same-name departments cannot be hidden by `LIMIT 1` selection;
- user repair SQL changes only `dept_id`/`update_time`, while role repair changes only that marked user's `sys_user_role` rows;
- all user and department conflicts are preflighted before writes;
- no runner/config/UI/schema changes entered this task.

Self-review and independent review found and fixed the issues documented in the two hardening RED sections. Review suggestions to validate role names/data scopes/non-button menu footprint or block `userType` on ordinary insert were not applied because they conflict with the binding Task 4 decisions: Task 4 validates business-role existence/status and exact governance button sets, and explicitly requires `userType` to be written on insert while forbidding arbitrary ordinary updates. No unresolved critical or important issue remains in the binding focused unit/mapper-contract scope.

## Concerns and follow-up

- Task 4 does not execute against real MySQL by design. Task 7 must verify generated-key behavior, transaction rollback, deleted marked-user recreation, exact role-link repair, and byte-for-byte password-hash stability.
- The baseline has no unique constraint on `sys_user.user_name`. Sequential reruns are idempotent as tested, but concurrent seeder invocations are not proven duplicate-safe without a database uniqueness/locking contract. The profile-gated runner should invoke provisioning once; Task 7 should either exercise or explicitly document the single-invocation assumption.
- Task 7 should confirm MyBatis maps `Set<String>` correctly for the permission query in the deployed runtime.
- Task 7 should execute the all-row username mapping and duplicate-department aggregate sentinel against the supported MySQL/MyBatis versions.

## Official Task 4 reviewer fixes after `c8a29869`

### Reviewer-fix RED

Command:

```powershell
mvn -pl ruoyi-system -am "-Dtest=FoundationTestIdentityMapperContractTest,FoundationTestIdentityProvisioningServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD FAILURE`, exit code 1 during `ruoyi-system:testCompile`, with 10 expected missing-method errors for `selectUsersByUserName(String)`. This proved the corrected all-row mapper contract was absent before implementation.

The RED tests also established the required service behavior for:

- active marked plus deleted real collision;
- multiple active marked rows;
- active marked plus valid `del_flag=2` history;
- only deleted marked history recreation;
- null, `1`, and `3` delete flags as conflicts;
- disabled active marked row as a conflict;
- differently-cased reserved department code as a conflict;
- persisted role-link delete-count mismatch as a coded transaction-aborting failure;
- every fresh-run department, user, BCrypt hash, and user-role payload.

### Reviewer-fix focused GREEN

Command:

```powershell
mvn -pl ruoyi-system -am "-Dtest=FoundationTestIdentityMapperContractTest,FoundationTestIdentityProvisioningServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`, exit code 0.

```text
FoundationTestIdentityMapperContractTest: 3 tests, 0 failures, 0 errors
FoundationTestIdentityProvisioningServiceTest: 19 tests, 0 failures, 0 errors
Total: 22 tests, 0 failures, 0 errors, 0 skipped
```

### Reviewer-fix broader GREEN

Command:

```powershell
mvn -pl ruoyi-system -am "-Dtest=FoundationTestIdentityMapperContractTest,FoundationTestIdentityProvisioningServiceTest,FoundationTestIdentityCatalogTest,FoundationTestPasswordPolicyTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`, exit code 0.

```text
FoundationTestIdentityCatalogTest: 3 tests
FoundationTestIdentityMapperContractTest: 3 tests
FoundationTestIdentityProvisioningServiceTest: 19 tests
FoundationTestPasswordPolicyTest: 4 tests
Total: 29 tests, 0 failures, 0 errors, 0 skipped
```

### Reviewer-fix implementation and self-review

- Replaced the single-row mapper API with `List<SysUser> selectUsersByUserName(String)` while preserving exactly 11 mapper methods.
- Username SQL is only `where u.user_name = #{userName}` and returns all logical-delete states. It has no `LIMIT`, `ORDER BY`, marker `LIKE`, `${}`, or catalog literal duplication.
- Java classification rejects any real/unmarked row, unknown/null delete flag, disabled active row, or second active marked row. Marked `del_flag=2` rows are history; exactly one active marked row is reusable; only valid history with no active row triggers recreation.
- Reusable department rows now require exact case-sensitive Java equality for both reserved code and name, in addition to creator/status/delete/tree checks.
- Fresh-run tests capture and validate all six inserted department payloads, all twelve inserted user payloads, real BCrypt matches with no plaintext storage, and all twelve exact user-role links against the catalog.
- Nonempty role-link replacement requires the delete count to equal the selected persisted-link count. Mismatch throws stable `USER_CONFLICT` after the delete and relies on the enclosing rollback transaction; the replacement insert is not attempted.
- `git diff --check` is clean. No runner, config, UI, role creation, role-menu mutation, schema migration, password rewrite, or broad repair was added.
