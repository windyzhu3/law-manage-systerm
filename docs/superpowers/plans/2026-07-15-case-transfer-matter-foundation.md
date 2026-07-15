# Case Transfer and Matter Intake Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把现有“缴费 → 商务交案/案件生成 → 分案 → 律师转案 → 案管接收”治理为强类型、对象权限明确、状态并发安全、事件可幂等且前后端可回归的业务底座。

**Architecture:** 保持现有模块化单体和数据库结构；`law-business` 提供写命令，`ruoyi-system` 的 Case AccessPolicy 和子域 Service 承担业务事实，MyBatis 只在持久化边界使用 Map，事务型 Outbox 与业务记录同事务。前端保留现有案件中心交互，先修复非法 UTF-8，再增加源码编码门禁和真实页面 E2E。

**Tech Stack:** Java 17、Spring Boot、Spring Transaction、Jakarta Validation、MyBatis、JUnit 5、Mockito、Vue 2、Element UI、Node.js、Playwright、Maven。

## Global Constraints

- 不新增 PRD 的 11 项转案材料、二次冲突审查、案管分类、业务线或非诉标签。
- 不新增业务表、字段、字典、Todo 模板、触发规则、事件类型或数据库迁移。
- 不改变现有菜单、权限编码、URL、前端 JSON 字段和状态编码。
- 查询投影可继续使用 `Map<String,Object>`；Controller、Facade、Service 和 Todo Handler 的写边界必须强类型。
- 客户端不能控制申请人、处理人、律师姓名、目标状态或事件幂等键。
- 事件幂等键只能使用业务记录 ID，不得包含随机 UUID。
- 每个行为严格执行 RED → GREEN → REFACTOR；测试未观察到预期失败前不得修改生产代码。
- 每个任务独立提交；不推送、不合并当前分支，除非用户另行授权。

---

## File Structure

### 新增文件

- `law-business/src/main/java/com/law/business/lawcase/dto/CaseBatchAssignmentCommand.java`：批量分案命令。
- `law-business/src/main/java/com/law/business/lawcase/dto/LawyerProfileSaveCommand.java`：律师档案保存命令。
- `law-business/src/main/java/com/law/business/lawcase/dto/LawyerProfileStatusCommand.java`：律师可分案状态命令。
- `law-business/src/test/java/com/law/business/lawcase/dto/CaseCommandValidationTest.java`：命令约束测试。
- `ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseAccessPolicy.java`：案件对象访问策略。
- `ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseTransferContext.java`：转案审批上下文。
- `ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseConfirmContext.java`：接案确认上下文。
- `ruoyi-system/src/test/java/com/ruoyi/system/service/casecenter/CaseAccessPolicyTest.java`：访问策略测试。
- `ruoyi-system/src/test/java/com/ruoyi/system/service/casecenter/CaseFacadeGuardTest.java`：强类型和随机事件结构守卫。
- `ruoyi-system/src/test/java/com/ruoyi/system/integration/CaseHandoffFoundationFlowTest.java`：全链路事务测试。
- `ruoyi-ui/scripts/check-source-encoding.js`：fatal UTF-8 源码检查。
- `ruoyi-ui/e2e/case-handoff-foundation.spec.js`：真实案件中心页面回归。

### 重点修改文件

- `law-business/.../CaseAssignmentCommand.java`
- `law-business/.../CaseTransferCommand.java`
- `law-business/.../CaseTransferApprovalCommand.java`
- `law-business/.../CaseConfirmCommand.java`
- `ruoyi-admin/.../BizCaseController.java`
- `ruoyi-system/.../IBizCaseService.java`
- `ruoyi-system/.../BizCaseServiceImpl.java`
- `ruoyi-system/.../casecenter/CaseQueryService.java`
- `ruoyi-system/.../casecenter/CaseCreationService.java`
- `ruoyi-system/.../casecenter/CaseAssignmentService.java`
- `ruoyi-system/.../casecenter/CaseTransferService.java`
- `ruoyi-system/.../casecenter/CaseConfirmationService.java`
- `ruoyi-system/.../casecenter/LawyerProfileService.java`
- `ruoyi-system/.../event/CaseCreationTodoHandler.java`
- `ruoyi-system/.../event/CaseAssignmentTodoHandler.java`
- `ruoyi-system/.../event/CaseTransferReviewTodoHandler.java`
- `ruoyi-system/.../event/CaseAcceptanceTodoHandler.java`
- `ruoyi-system/.../event/ContractTodoValidator.java`
- `ruoyi-system/.../mapper/BizCaseMapper.java`
- `ruoyi-system/.../mapper/BizContractMapper.java`
- `ruoyi-system/src/main/resources/mapper/system/BizCaseMapper.xml`
- `ruoyi-system/src/main/resources/mapper/system/BizContractMapper.xml`
- `ruoyi-ui/src/views/case/index.vue`
- `ruoyi-ui/package.json`
- `.github/workflows/ci.yml`
- `doc/v0.2-prd-readiness-gap-analysis.md`

---

### Task 1: Establish Strong-Typed Case Commands

**Files:**
- Create: `law-business/src/main/java/com/law/business/lawcase/dto/CaseBatchAssignmentCommand.java`
- Create: `law-business/src/main/java/com/law/business/lawcase/dto/LawyerProfileSaveCommand.java`
- Create: `law-business/src/main/java/com/law/business/lawcase/dto/LawyerProfileStatusCommand.java`
- Modify: `law-business/src/main/java/com/law/business/lawcase/dto/CaseAssignmentCommand.java`
- Modify: `law-business/src/main/java/com/law/business/lawcase/dto/CaseTransferCommand.java`
- Modify: `law-business/src/main/java/com/law/business/lawcase/dto/CaseTransferApprovalCommand.java`
- Modify: `law-business/src/main/java/com/law/business/lawcase/dto/CaseConfirmCommand.java`
- Test: `law-business/src/test/java/com/law/business/lawcase/dto/CaseCommandValidationTest.java`

**Interfaces:**
- Produces: Jackson-compatible JavaBean commands; no `toPersistenceMap()` methods.
- Produces: `CaseBatchAssignmentCommand#getCaseIds()` with `@NotEmpty`.
- Produces: `LawyerProfileSaveCommand` and `LawyerProfileStatusCommand` for the remaining case Controller writes.

- [x] **Step 1: Write failing validation and reflection tests**

```java
class CaseCommandValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test void batchAssignmentRequiresCases() {
        CaseBatchAssignmentCommand command = new CaseBatchAssignmentCommand();
        command.setMainLawyerId(12L);
        command.setAssignMethod("manual");
        command.setPriority("medium");
        command.setAssignReason("normal");
        assertTrue(validator.validate(command).stream()
                .anyMatch(v -> "请选择案件".equals(v.getMessage())));
    }

    @Test void writeCommandsDoNotExposePersistenceMaps() {
        for (Class<?> type : List.of(CaseAssignmentCommand.class,
                CaseTransferCommand.class, CaseTransferApprovalCommand.class,
                CaseConfirmCommand.class)) {
            assertTrue(Arrays.stream(type.getDeclaredMethods())
                    .noneMatch(method -> method.getName().equals("toPersistenceMap")));
        }
    }
}
```

- [x] **Step 2: Run RED**

Run:

```powershell
mvn --batch-mode --no-transfer-progress -pl law-business -Dtest=CaseCommandValidationTest test
```

Expected: test compilation fails because the three new command classes do not exist and current commands still expose `toPersistenceMap()`.

- [x] **Step 3: Implement the commands**

Implement `CaseBatchAssignmentCommand` with the same assign fields as the single command and:

```java
@NotEmpty(message = "请选择案件")
private List<@NotNull Long> caseIds;
```

Add the UI-supported `estimatedCycle` and `planStartDate` fields to both assignment commands. Keep `assistantLawyerIds` JSON-compatible, but do not expose `assistantLawyerNames` as an authoritative service fact. Implement lawyer commands with:

```java
public class LawyerProfileStatusCommand {
    @NotNull(message = "请选择律师") private Long userId;
    @NotBlank(message = "请选择可分案状态") private String assignEnabled;
    // getters and setters
}
```

Delete all `toPersistenceMap()` methods from case write commands.

- [x] **Step 4: Run GREEN**

Run the Task 1 Maven command. Expected: `CaseCommandValidationTest` passes.

- [x] **Step 5: Commit**

```powershell
git add law-business/src/main/java/com/law/business/lawcase/dto law-business/src/test/java/com/law/business/lawcase/dto
git commit -m "refactor: define typed case commands"
```

---

### Task 2: Centralize Case Object Access

**Files:**
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseAccessPolicy.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseTransferContext.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseConfirmContext.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseQueryService.java`
- Test: `ruoyi-system/src/test/java/com/ruoyi/system/service/casecenter/CaseAccessPolicyTest.java`

**Interfaces:**
- Consumes: `BusinessActorProvider.current()` and explicit `BusinessActor` from Todo handlers.
- Produces: `requireReadable`, `requireAssignable`, `requireTransferRequestable`, `requireTransferApprovable`, `requireConfirmable`.

- [x] **Step 1: Write failing policy tests**

```java
@Test void onlyAssignedLawyerOrOwnerCanRequestTransfer() {
    when(mapper.selectCaseById(8L)).thenReturn(Map.of(
            "case_id", 8L, "case_status", "processing",
            "owner_id", 3L, "main_lawyer_id", 12L));
    when(mapper.countCaseInDataScope(8L, 99L, 4L, true, CasePermissions.DATA_SCOPE))
            .thenReturn(1);
    ServiceException error = assertThrows(ServiceException.class,
            () -> policy.requireTransferRequestable(8L,
                    new BusinessActor(99L, "other", "other", 4L, false)));
    assertEquals("ACCESS_DENIED", error.getBusinessCode());
}

@Test void onlyNamedRecipientCanConfirm() {
    when(mapper.selectConfirmById(31L)).thenReturn(Map.of(
            "confirm_id",31L,"confirm_status","pending","confirm_user_id",12L,
            "case_id",8L,"caseStatus","confirming"));
    ServiceException error = assertThrows(ServiceException.class,
            () -> policy.requireConfirmable(31L,
                    new BusinessActor(13L,"bob","Bob",4L,false)));
    assertEquals("ACCESS_DENIED", error.getBusinessCode());
}
```

- [x] **Step 2: Run RED**

```powershell
mvn --batch-mode --no-transfer-progress -pl ruoyi-system -am -Dtest=CaseAccessPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: test compilation fails because `CaseAccessPolicy` is absent.

- [x] **Step 3: Implement policy and typed contexts**

Use exact signatures:

```java
public Map<String,Object> requireReadable(Long caseId, BusinessActor actor)
public Map<String,Object> requireAssignable(Long caseId, BusinessActor actor)
public Map<String,Object> requireTransferRequestable(Long caseId, BusinessActor actor)
public CaseTransferContext requireTransferApprovable(Long transferId, BusinessActor actor)
public CaseConfirmContext requireConfirmable(Long confirmId, BusinessActor actor)
```

`requireTransferRequestable` allows an administrator, `owner_id`, or `main_lawyer_id`. `requireConfirmable` requires `pending`, case `confirming`, and actor matching `confirm_user_id` unless administrator. All errors use `BusinessErrorCode`.

Refactor `CaseQueryService.caseDetail()` and `requireAccess()` to delegate to the policy without changing query payloads.

- [x] **Step 4: Run GREEN and existing case tests**

```powershell
mvn --batch-mode --no-transfer-progress -pl ruoyi-system -am '-Dtest=CaseAccessPolicyTest,CaseAssignmentServiceTest,CaseTransferServiceTest,CaseConfirmationServiceTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all selected tests pass.

- [x] **Step 5: Commit**

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseAccessPolicy.java ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseTransferContext.java ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseConfirmContext.java ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseQueryService.java ruoyi-system/src/test/java/com/ruoyi/system/service/casecenter/CaseAccessPolicyTest.java
git commit -m "refactor: centralize case access policy"
```

---

### Task 3: Stabilize Paid Contract to Case Creation

**Files:**
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseCreationService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/CaseCreationTodoHandler.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/ContractTodoValidator.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizContractMapper.java`
- Modify: `ruoyi-system/src/main/resources/mapper/system/BizContractMapper.xml`
- Modify: `ruoyi-system/src/test/java/com/ruoyi/system/service/casecenter/CaseCreationServiceTest.java`
- Modify: `ruoyi-system/src/test/java/com/ruoyi/system/integration/ContractToCaseTodoFlowTest.java`

**Interfaces:**
- Produces: `int createFromContract(Long contractId, BusinessActor actor)`.
- Produces: mapper `int countConfirmedFeePlans(Long contractId)`.
- Produces: idempotency key `CASE_CREATED:{caseId}`.

- [x] **Step 1: Write failing creation tests**

Add tests that assert:

```java
verify(events).publish(argThat(event ->
        "CASE_CREATED:81".equals(event.getIdempotencyKey())));
```

and:

```java
when(contracts.countConfirmedFeePlans(10L)).thenReturn(0);
ServiceException error = assertThrows(ServiceException.class,
        () -> service.createFromContract(10L, actor()));
assertEquals("PRECONDITION_FAILED", error.getBusinessCode());
verify(cases, never()).insertCase(anyMap());
```

Also assert an existing case returns without publishing another event.

- [x] **Step 2: Run RED**

```powershell
mvn --batch-mode --no-transfer-progress -pl ruoyi-system -am '-Dtest=CaseCreationServiceTest,ContractToCaseTodoFlowTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation or assertions fail because creation accepts `BizContract`, does not check confirmed payment, and publishes a UUID key.

- [x] **Step 3: Implement creation eligibility and stable event**

Add mapper SQL:

```xml
<select id="countConfirmedFeePlans" resultType="int">
  select count(1) from biz_contract_fee_plan
  where contract_id=#{contractId} and confirm_status='confirmed'
</select>
```

`CaseCreationService` reloads the contract by ID, requires `sign_status='1'`, requires confirmed fee count greater than zero, relies on existing unique `biz_case.contract_id`, and publishes:

```java
new BusinessEventCommand(BusinessEventType.CASE_CREATED, "CASE", caseId, caseNo,
        "CASE_CREATED:" + caseId, Map.of("contractId", contractId))
```

Update the Todo handler to call `createFromContract(todo.getBusinessId(), actor)`. The validator repeats the payment precondition for a user-facing DoD error, while Service remains authoritative.

- [x] **Step 4: Run GREEN**

Run the Task 3 Maven command. Expected: all selected tests pass.

- [x] **Step 5: Commit**

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseCreationService.java ruoyi-system/src/main/java/com/ruoyi/system/service/event/CaseCreationTodoHandler.java ruoyi-system/src/main/java/com/ruoyi/system/service/event/ContractTodoValidator.java ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizContractMapper.java ruoyi-system/src/main/resources/mapper/system/BizContractMapper.xml ruoyi-system/src/test/java/com/ruoyi/system/service/casecenter/CaseCreationServiceTest.java ruoyi-system/src/test/java/com/ruoyi/system/integration/ContractToCaseTodoFlowTest.java
git commit -m "refactor: stabilize paid contract case creation"
```

---

### Task 4: Make Assignment Typed, Atomic, and Event-Stable

**Files:**
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseAssignmentService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizCaseMapper.java`
- Modify: `ruoyi-system/src/main/resources/mapper/system/BizCaseMapper.xml`
- Modify: `ruoyi-system/src/test/java/com/ruoyi/system/service/casecenter/CaseAssignmentServiceTest.java`

**Interfaces:**
- Consumes: `CaseAssignmentCommand`, `CaseBatchAssignmentCommand`, `CaseAccessPolicy`.
- Produces: `assign(command, actor)` and `batchAssign(command, actor)`.
- Produces: `CASE_ASSIGNED:{caseId}:{assignmentId}` with `confirmId` in payload when required.

- [x] **Step 1: Write failing assignment tests**

```java
when(mapper.insertAssignment(anyMap())).thenAnswer(invocation -> {
    invocation.<Map<String,Object>>getArgument(0).put("assignmentId", 41L);
    return 1;
});
when(mapper.insertConfirm(anyMap())).thenAnswer(invocation -> {
    invocation.<Map<String,Object>>getArgument(0).put("confirmId", 51L);
    return 1;
});
service.assign(command(8L), actor());
verify(events).publish(argThat(event ->
        "CASE_ASSIGNED:8:41".equals(event.getIdempotencyKey())
        && Long.valueOf(51L).equals(event.getPayload().get("confirmId"))));
```

Add a batch test where the second case fails and a transaction fixture restores the first case, its assignment, log and event.

- [x] **Step 2: Run RED**

```powershell
mvn --batch-mode --no-transfer-progress -pl ruoyi-system -am -Dtest=CaseAssignmentServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails on typed signatures or stable-key assertion fails because UUID is present.

- [x] **Step 3: Implement typed assignment**

Convert commands to Mapper parameter maps only in private `assignmentRow(...)`. Derive names and actor fields in Service. Capture generated `assignmentId` and `confirmId`, then publish:

```java
String key = "CASE_ASSIGNED:" + caseId + ":" + assignmentId;
```

Call `CaseStatusTransitions.requireAllowed(PENDING, target)` before `updateCaseAssignment`. Keep `batchAssign` as one public `@Transactional` method calling a non-proxied private `assignOne`, so one failure rolls back all rows.

- [x] **Step 4: Run GREEN**

Run Task 4 test, then:

```powershell
mvn --batch-mode --no-transfer-progress -pl ruoyi-system -am '-Dtest=CaseAssignmentServiceTest,CaseManagementTodoFlowTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: selected tests pass.

- [x] **Step 5: Commit**

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseAssignmentService.java ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizCaseMapper.java ruoyi-system/src/main/resources/mapper/system/BizCaseMapper.xml ruoyi-system/src/test/java/com/ruoyi/system/service/casecenter/CaseAssignmentServiceTest.java
git commit -m "refactor: stabilize case assignment facts"
```

---

### Task 5: Make Lawyer Transfer Typed and Event-Stable

**Files:**
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseTransferService.java`
- Modify: `ruoyi-system/src/test/java/com/ruoyi/system/service/casecenter/CaseTransferServiceTest.java`

**Interfaces:**
- Consumes: typed transfer commands and explicit `BusinessActor`.
- Produces: `CASE_TRANSFER_REQUESTED:{caseId}:{transferId}` and `CASE_TRANSFER_APPROVED:{caseId}:{transferId}`.

- [x] **Step 1: Write failing transfer tests**

Assert the request actor is enforced by `CaseAccessPolicy`, generated `transferId` is used in the request event, and approval event includes generated `confirmId`:

```java
verify(publisher).publish(argThat(event ->
        "CASE_TRANSFER_REQUESTED:8:61".equals(event.getIdempotencyKey())));
verify(publisher).publish(argThat(event ->
        "CASE_TRANSFER_APPROVED:8:61".equals(event.getIdempotencyKey())
        && Long.valueOf(71L).equals(event.getPayload().get("confirmId"))));
```

- [x] **Step 2: Run RED**

```powershell
mvn --batch-mode --no-transfer-progress -pl ruoyi-system -am -Dtest=CaseTransferServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: typed method calls do not compile or UUID key assertions fail.

- [x] **Step 3: Implement typed request and approval**

Use `CaseAccessPolicy` contexts; derive applicant and approver fields from actor. Call transitions:

```java
CaseStatusTransitions.requireAllowed(CaseStatus.PROCESSING, CaseStatus.TRANSFERRING);
CaseStatusTransitions.requireAllowed(CaseStatus.TRANSFERRING,
        passed ? CaseStatus.CONFIRMING : CaseStatus.PROCESSING);
```

Keep the existing actions `passed/rejected/supplement`. Build persistence maps privately and publish only after transfer, case status, confirmation, log and notice writes succeed.

- [x] **Step 4: Run GREEN**

Run Task 5 test. Expected: all transfer tests pass.

- [x] **Step 5: Commit**

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseTransferService.java ruoyi-system/src/test/java/com/ruoyi/system/service/casecenter/CaseTransferServiceTest.java
git commit -m "refactor: stabilize case transfer facts"
```

---

### Task 6: Secure and Stabilize Case Acceptance

**Files:**
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseConfirmationService.java`
- Modify: `ruoyi-system/src/main/resources/mapper/system/BizCaseMapper.xml`
- Modify: `ruoyi-system/src/test/java/com/ruoyi/system/service/casecenter/CaseConfirmationServiceTest.java`

**Interfaces:**
- Consumes: `CaseConfirmCommand`, `CaseAccessPolicy.requireConfirmable`.
- Produces: stable accepted/rejected events tied to `confirmId`.

- [x] **Step 1: Write failing acceptance tests**

```java
@Test void namedRecipientProducesStableAcceptanceFact() {
    service.handle(command(31L, "accepted"), actor(12L));
    verify(publisher).publish(argThat(event ->
            "CASE_ACCEPTED:8:31".equals(event.getIdempotencyKey())));
}

@Test void caseUpdateRequiresConfirmingState() {
    service.handle(command(31L, "rejected"), actor(12L));
    verify(mapper).updateCaseConfirmResult(argThat(row ->
            "confirming".equals(row.get("expectedStatus"))));
}
```

- [x] **Step 2: Run RED**

```powershell
mvn --batch-mode --no-transfer-progress -pl ruoyi-system -am -Dtest=CaseConfirmationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: typed signature or stable key assertion fails; current Mapper update has no exact expected status.

- [x] **Step 3: Implement secure confirmation**

Use the policy context, validate transition, set `expectedStatus='confirming'`, and change SQL to:

```xml
where case_id=#{caseId}
  and del_flag='0'
  and case_status=#{expectedStatus}
```

Publish `CASE_ACCEPTED:{caseId}:{confirmId}` or `CASE_REJECTED:{caseId}:{confirmId}` after all writes.

- [x] **Step 4: Run GREEN**

Run Task 6 test. Expected: all confirmation tests pass.

- [x] **Step 5: Commit**

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseConfirmationService.java ruoyi-system/src/main/resources/mapper/system/BizCaseMapper.xml ruoyi-system/src/test/java/com/ruoyi/system/service/casecenter/CaseConfirmationServiceTest.java
git commit -m "refactor: secure case acceptance facts"
```

---

### Task 7: Complete Typed Facade, Controller, Todo Handlers, and Lawyer Profiles

**Files:**
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/lawcase/BizCaseController.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/IBizCaseService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizCaseServiceImpl.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/LawyerProfileService.java`
- Modify: four case Todo handlers under `ruoyi-system/src/main/java/com/ruoyi/system/service/event/`
- Modify: `ruoyi-system/src/test/java/com/ruoyi/system/integration/CaseManagementTodoFlowTest.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/casecenter/CaseFacadeGuardTest.java`

**Interfaces:**
- Produces: no case write `Map` signatures in Controller, facade, child services or Todo handlers.
- Keeps existing JSON and URL contracts.

- [x] **Step 1: Write failing structural guard**

```java
@Test void caseWriteBoundariesAreTyped() throws Exception {
    String controller = source("ruoyi-admin/src/main/java/com/ruoyi/web/controller/lawcase/BizCaseController.java");
    String facade = source("ruoyi-system/src/main/java/com/ruoyi/system/service/IBizCaseService.java");
    assertFalse(controller.matches("(?s).*@RequestBody\\s+Map<.*"));
    assertFalse(facade.matches("(?s).*(assignCase|batchAssignCases|requestTransfer|approveTransfer|handleConfirm|saveLawyerProfile|updateLawyerProfileStatus)\\(Map<.*"));
}

@Test void caseServicesDoNotBuildRandomEventKeys() throws Exception {
    for (Path file : Files.list(caseServiceDirectory()).toList()) {
        String source = Files.readString(file);
        assertFalse(source.contains("IdUtils.fastUUID()"), file.toString());
    }
}
```

- [x] **Step 2: Run RED**

```powershell
mvn --batch-mode --no-transfer-progress -pl ruoyi-system -am -Dtest=CaseFacadeGuardTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: guard fails on Map signatures and/or random keys.

- [x] **Step 3: Refactor facade and handlers**

Use exact facade signatures:

```java
int assignCase(CaseAssignmentCommand command);
int batchAssignCases(CaseBatchAssignmentCommand command);
int requestTransfer(CaseTransferCommand command);
int approveTransfer(CaseTransferApprovalCommand command);
int handleConfirm(CaseConfirmCommand command);
int saveLawyerProfile(LawyerProfileSaveCommand command);
int updateLawyerProfileStatus(LawyerProfileStatusCommand command);
int createCaseFromContract(Long contractId);
```

Controller passes commands directly. Todo handlers instantiate typed commands and call child services with explicit actor. Lawyer profile service derives audit fields from `BusinessActorProvider`; it ignores any client-supplied names or audit fields.

- [x] **Step 4: Run GREEN and module compilation**

```powershell
mvn --batch-mode --no-transfer-progress -pl ruoyi-admin -am '-Dtest=CaseFacadeGuardTest,CaseManagementTodoFlowTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: selected tests and compilation of all dependent modules pass.

- [x] **Step 5: Commit**

```powershell
git add law-business/src/main/java/com/law/business/lawcase/dto ruoyi-admin/src/main/java/com/ruoyi/web/controller/lawcase/BizCaseController.java ruoyi-system/src/main/java/com/ruoyi/system/service/IBizCaseService.java ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizCaseServiceImpl.java ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/LawyerProfileService.java ruoyi-system/src/main/java/com/ruoyi/system/service/event ruoyi-system/src/test/java/com/ruoyi/system/integration/CaseManagementTodoFlowTest.java ruoyi-system/src/test/java/com/ruoyi/system/service/casecenter/CaseFacadeGuardTest.java
git commit -m "refactor: enforce typed case api commands"
```

---

### Task 8: Prove the Case Handoff Transaction Chain

**Files:**
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/integration/CaseHandoffFoundationFlowTest.java`
- Modify: relevant case unit fixtures only if shared builders are needed.

**Interfaces:**
- Consumes: final typed services from Tasks 3–7.
- Produces: evidence for event order and rollback on Outbox failure.

- [x] **Step 1: Write failing full-flow test**

Build a stateful fixture like `CustomerContractPaymentFlowTest` and assert:

```java
assertEquals(List.of(
    "CASE_CREATED:81",
    "CASE_ASSIGNED:81:91",
    "CASE_ACCEPTED:81:101",
    "CASE_TRANSFER_REQUESTED:81:111",
    "CASE_TRANSFER_APPROVED:81:111",
    "CASE_ACCEPTED:81:121"), events.keys());
assertEquals("processing", caseState.status());
assertEquals(13L, caseState.mainLawyerId());
```

Add a second test whose publisher throws during transfer approval; `TransactionTemplate` must restore transfer status, case status, confirmation list and action logs.

- [x] **Step 2: Run RED**

```powershell
mvn --batch-mode --no-transfer-progress -pl ruoyi-system -am -Dtest=CaseHandoffFoundationFlowTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: at least one assertion fails if any event key, actor check, conditional update or rollback boundary is incomplete.

- [x] **Step 3: Make only the minimal service/fixture corrections**

Do not weaken assertions. Correct production transaction ordering only where the failing test proves a defect. Ensure publisher calls remain last within each transaction.

- [x] **Step 4: Run GREEN and all case tests**

```powershell
mvn --batch-mode --no-transfer-progress -pl ruoyi-system -am '-Dtest=Case*Test,ContractToCaseTodoFlowTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all matching case and contract-to-case tests pass.

- [x] **Step 5: Commit**

```powershell
git add ruoyi-system/src/test/java/com/ruoyi/system/integration/CaseHandoffFoundationFlowTest.java ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter
git commit -m "test: cover case handoff foundation flow"
```

---

### Task 9: Repair Case Page Encoding and Add a Source Gate

**Files:**
- Create: `ruoyi-ui/scripts/check-source-encoding.js`
- Modify: `ruoyi-ui/src/views/case/index.vue`
- Modify: `ruoyi-ui/package.json`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Produces: `npm run test:encoding` with fatal UTF-8 decoding.
- Preserves: `case-page-actions.js`, existing components, modes, URLs and permissions.

- [x] **Step 1: Add the encoding checker and observe RED**

```js
const fs = require('fs')
const path = require('path')
const decoder = new TextDecoder('utf-8', { fatal: true })

function visit(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const file = path.join(dir, entry.name)
    if (entry.isDirectory()) visit(file)
    else if (/\.(vue|js|scss)$/.test(entry.name)) decoder.decode(fs.readFileSync(file))
  }
}
visit(path.resolve(__dirname, '../src'))
console.log('source encoding ok')
```

Run:

```powershell
Set-Location ruoyi-ui
node scripts/check-source-encoding.js
```

Expected: FAIL with invalid UTF-8 in `src/views/case/index.vue` around byte 15000.

- [x] **Step 2: Restore the case page from the last valid semantic version**

Use `50814311^:ruoyi-ui/src/views/case/index.vue` only as the valid template baseline. Reconnect the current extracted `case-page-actions.js`, `CaseActionDialogs.vue`, resource drawers and SCSS. Do not reintroduce the old monolithic script or styles.

- [x] **Step 3: Add package and CI gates**

Add:

```json
"test:encoding": "node scripts/check-source-encoding.js"
```

Run `npm run test:encoding` before the production build in `.github/workflows/ci.yml`.

- [x] **Step 4: Run GREEN and frontend build**

```powershell
Set-Location ruoyi-ui
npm run test:encoding
npm run test:todo
npm run build:prod
```

Expected: encoding check prints `source encoding ok`; Todo UI test passes; production build succeeds with only existing bundle-size warnings.

- [x] **Step 5: Commit**

```powershell
git add ruoyi-ui/scripts/check-source-encoding.js ruoyi-ui/src/views/case/index.vue ruoyi-ui/package.json .github/workflows/ci.yml
git commit -m "fix: restore case center source integrity"
```

---

### Task 10: Cover the Real Case Center Page

**Files:**
- Create: `ruoyi-ui/e2e/case-handoff-foundation.spec.js`
- Modify: `ruoyi-ui/e2e/fixtures.js` only for reusable route helpers.

**Interfaces:**
- Produces: mocked API E2E for assignment, transfer request/approval and acceptance.

- [x] **Step 1: Write failing page E2E**

Set up the authenticated case route and capture requests. Include error collection:

```js
const errors = []
page.on('pageerror', error => errors.push(error.message))
page.on('console', message => {
  if (message.type() === 'error') errors.push(message.text())
})
```

Test these payloads:

```js
expect(assignBody.mainLawyerId).toBe(12)
expect(transferBody).toMatchObject({ caseId: 81, toLawyerId: 13 })
expect(approvalBody).toMatchObject({ transferId: 111, action: 'passed' })
expect(confirmBody).toEqual({ confirmId: 121, confirmResult: 'accepted', remark: '确认接收' })
expect(errors).toEqual([])
```

- [x] **Step 2: Run RED**

```powershell
Set-Location ruoyi-ui
npx playwright test e2e/case-handoff-foundation.spec.js
```

Expected: test fails before all required case routes and restored controls are wired correctly.

- [x] **Step 3: Correct page wiring without adding features**

Fix only request mapping, component props/emits, mode routing or restored template bindings proven by E2E. Keep existing labels, permissions and endpoint paths.

- [x] **Step 4: Run GREEN and complete Playwright**

```powershell
npx playwright test e2e/case-handoff-foundation.spec.js
npx playwright test
```

Expected: the new spec and entire suite pass with zero page/console errors.

- [x] **Step 5: Commit**

```powershell
git add ruoyi-ui/e2e/case-handoff-foundation.spec.js ruoyi-ui/e2e/fixtures.js ruoyi-ui/src/views/case
git commit -m "test: cover case handoff page flows"
```

---

### Task 11: Full Verification, Scope Audit, and Readiness Evidence

**Files:**
- Modify: `docs/superpowers/plans/2026-07-15-case-transfer-matter-foundation.md`
- Modify: `doc/v0.2-prd-readiness-gap-analysis.md`

**Interfaces:**
- Produces: requirement-by-requirement completion evidence.
- Produces: explicit CI-only status for database tests when local DB is absent.

- [x] **Step 1: Run backend full verification**

```powershell
mvn --batch-mode --no-transfer-progress clean verify
```

Expected: all modules `SUCCESS`, zero test failures/errors. Record executed/skipped counts from Surefire XML, not only the Maven exit code.

- [x] **Step 2: Run frontend full verification**

```powershell
Set-Location ruoyi-ui
npm run test:encoding
npm run test:todo
npm run build:prod
npx playwright test
```

Expected: encoding and Todo checks pass, production build succeeds, entire Playwright suite passes.

- [x] **Step 3: Run database-targeted tests**

```powershell
Set-Location ..
mvn --batch-mode --no-transfer-progress -pl ruoyi-admin -am '-Dtest=FlywayMigrationTest,PhaseTwoDatabaseInvariantTest,TodoPhaseTwoTransactionTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: execute and pass when `TODO_MIGRATION_DB_URL` is configured. If absent, record each skipped test and leave the database CI gate explicitly pending.

- [x] **Step 4: Run scope and structure audit**

```powershell
rg -n "@RequestBody\s+Map|assignCase\(Map|batchAssignCases\(Map|requestTransfer\(Map|approveTransfer\(Map|handleConfirm\(Map|IdUtils\.fastUUID" ruoyi-admin/src/main/java/com/ruoyi/web/controller/lawcase ruoyi-system/src/main/java/com/ruoyi/system/service/IBizCaseService.java ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizCaseServiceImpl.java ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter ruoyi-system/src/main/java/com/ruoyi/system/service/event
git diff 0184b25e --name-only | rg "db/migration|BusinessEventType.java"
git diff --check
git status --short
```

Expected: first two searches produce no prohibited matches; diff check is clean. Generated `dist`, reports, screenshots and `test-results` are removed before commit.

- [x] **Step 5: Update evidence documents**

Mark each completed checkbox in this plan only after its command evidence exists. Add to `doc/v0.2-prd-readiness-gap-analysis.md`:

- strong-type and access-policy status;
- six stable event-key formats;
- backend/frontend/E2E counts;
- UTF-8 gate status;
- database tests executed or honestly skipped;
- statement that 11 materials, conflict and classification remain v0.2 work.

- [x] **Step 6: Commit documentation**

```powershell
git add docs/superpowers/plans/2026-07-15-case-transfer-matter-foundation.md doc/v0.2-prd-readiness-gap-analysis.md
git commit -m "docs: close case handoff foundation governance"
```

---

## Completion Gate

Do not mark this plan complete unless current evidence proves all of the following:

- [x] all case write boundaries are typed;
- [x] object access, transfer actor and confirmation recipient checks are tested;
- [x] case creation requires signed contract and a confirmed payment;
- [x] six existing case events have stable business keys;
- [x] state changes use exact conditional updates;
- [x] Outbox failure rolls back business state and logs;
- [x] case center source is valid UTF-8 and protected by CI;
- [x] actual case page and the full Playwright suite pass;
- [x] full Maven verify passes;
- [x] no new v0.2 schema, dictionary, Todo definition or event type was introduced;
- [x] database migration tests are either executed successfully or explicitly left as a CI gate.
