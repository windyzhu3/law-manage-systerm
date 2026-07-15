# 合同与收费链路基础治理实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不新增 v0.2 业务功能、不破坏现有接口的前提下，将客户→合同→审批→签署→缴费链路治理为强类型、对象权限集中、条件更新、稳定幂等且具备自动化安全网的业务事实层。

**Architecture:** 保留 `IBizContractService`、`BizContractController`、`BizFinanceController` 和现有 URL 作为兼容边界，`BizContractServiceImpl` 降级为纯 Facade。业务行为拆入合同子服务，统一通过 `ContractAccessPolicy` 访问对象；动作记录先持久化并取得稳定主键，再在同一事务写 Outbox 事件。

**Tech Stack:** Java 17、Spring Boot 3、Jakarta Validation、MyBatis、MySQL 8、JUnit 5、Mockito、MockMvc、Vue 2、Element UI、Playwright、GitHub Actions。

## Global Constraints

- 不新增报价、冲突审查、催收、风险代理、转案材料、案管分类、v0.2 页面、Todo 模板或事件类型。
- 不新增数据库表或 v0.2 字段；只允许调整既有 Mapper 条件和自增主键回传。
- 保持 `/contract/**`、`/finance/**` URL、HTTP 方法、权限编码、JSON 字段和响应结构兼容。
- 所有 Controller/Service 写入口必须使用强类型命令；查询 Map 和 Mapper 内部持久化 Map 可以保留。
- 所有状态动作采用条件更新；0 行更新统一抛 `CONCURRENT_MODIFICATION`。
- 现有事件幂等键不得包含 UUID，必须绑定已持久化动作 ID。
- 业务事实、动作记录、附件与 Outbox 必须在同一事务中提交或回滚。
- 生产代码变更必须遵循先失败、后通过的 TDD 顺序。

---

### Task 1: 建立合同写入命令契约

**Files:**
- Create: `law-business/src/main/java/com/law/business/contract/dto/ContractCreateCommand.java`
- Create: `law-business/src/main/java/com/law/business/contract/dto/ContractUpdateCommand.java`
- Create: `law-business/src/main/java/com/law/business/contract/dto/ContractNumberRuleUpdateCommand.java`
- Create: `law-business/src/main/java/com/law/business/contract/dto/ContractTemplateCreateCommand.java`
- Create: `law-business/src/main/java/com/law/business/contract/dto/ContractTemplateUpdateCommand.java`
- Create: `law-business/src/main/java/com/law/business/contract/dto/ContractFeePlanCreateCommand.java`
- Create: `law-business/src/main/java/com/law/business/contract/dto/ContractFeePlanUpdateCommand.java`
- Create: `law-business/src/main/java/com/law/business/contract/dto/ContractAttachmentCreateCommand.java`
- Modify: `law-business/src/main/java/com/law/business/contract/dto/ContractApprovalCommand.java`
- Modify: `law-business/src/main/java/com/law/business/contract/dto/ContractReasonCommand.java`
- Modify: `law-business/src/main/java/com/law/business/contract/dto/ContractSignCommand.java`
- Modify: `law-business/src/main/java/com/law/business/contract/dto/FeeConfirmCommand.java`
- Modify: `law-business/src/main/java/com/law/business/contract/dto/FeeRejectCommand.java`
- Modify: `law-business/src/main/java/com/law/business/contract/dto/FeeInvoiceCommand.java`
- Create: `law-business/src/test/java/com/law/business/contract/dto/ContractCommandValidationTest.java`

**Interfaces:**
- Produces: Jakarta Validation 命令对象；字段名与现有 Vue 表单字段一致。
- Does not produce: 审核状态、签署状态、实收状态等可由客户端任意指定的字段。

- [x] **Step 1: 写命令校验失败测试**

```java
class ContractCommandValidationTest {
  private final Validator validator=Validation.buildDefaultValidatorFactory().getValidator();
  @Test void createRequiresCustomerNameCaseAndPositiveAmount(){
    ContractCreateCommand c=new ContractCreateCommand();c.setSignAmount(BigDecimal.ZERO);
    assertEquals(Set.of("contractName","customerId","caseType","signAmount"),fields(validator.validate(c)));
  }
  @Test void feePlanUsesCurrentFrontendFieldNames(){
    ContractFeePlanCreateCommand c=new ContractFeePlanCreateCommand();
    assertTrue(fields(validator.validate(c)).containsAll(Set.of("contractId","periodNo","receivableAmount","planReceiveDate")));
  }
  private Set<String> fields(Set<? extends ConstraintViolation<?>> xs){return xs.stream().map(x->x.getPropertyPath().toString()).collect(Collectors.toSet());}
}
```

- [x] **Step 2: 运行测试并确认类不存在**

Run: `mvn -pl law-business -Dtest=ContractCommandValidationTest test`
Expected: FAIL，编译错误包含 `cannot find symbol ContractCreateCommand`。

- [x] **Step 3: 实现命令对象**

`ContractCreateCommand` 和 `ContractUpdateCommand` 使用 `BigDecimal signAmount`、`LocalDate signDate/effectiveDate/expireDate`；`contractName/customerId/caseType/signAmount` 必填。`ContractFeePlan*Command` 使用 `Integer periodNo`、`BigDecimal receivableAmount`、`LocalDate planReceiveDate`，四个创建字段全部必填。模板的 `templateName/caseType/fileName/fileUrl` 必填；附件的 `contractId/fileName/fileUrl/fileType` 必填。所有字符串加明确 `@Size`，金额使用 `@DecimalMin("0.01")`，期次使用 `@Min(1)`。更新命令增加 `@NotNull contractId/planId/templateId`，但不定义目标状态字段。

编号规则字段固定为：

```java
@NotNull Long ruleId;
@NotBlank @Size(max=80) String ruleName;
@NotBlank @Size(max=20) String prefix;
@NotBlank @Size(max=20) String datePattern;
@NotNull @Min(3) @Max(12) Integer serialLength;
@NotBlank String status;
@Size(max=500) String remark;
```

- [x] **Step 4: 补强现有动作命令**

审批意见和原因使用 `@NotBlank @Size(max=500)`；签署状态和开票状态使用 `@Pattern` 约束现有允许值；收款金额继续为字符串以保持 JSON 兼容，并保留 `@DecimalMin("0.01")`。`FeeConfirmCommand` 增加可选 `remark/paymentMethod/voucherUrl`，`FeeInvoiceCommand` 增加可选 `remark/invoiceType`，使合同页面和财务页面都能映射到同一个强类型合同接口；旧请求不传这些字段时行为不变。

- [x] **Step 5: 运行命令测试**

Run: `mvn -pl law-business -Dtest=ContractCommandValidationTest test`
Expected: PASS，0 failures。

- [x] **Step 6: 提交**

```powershell
git add law-business/src/main/java/com/law/business/contract/dto law-business/src/test/java/com/law/business/contract/dto
git commit -m "refactor: define typed contract finance commands"
```

### Task 2: 集中合同对象访问策略

**Files:**
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractFeePlanContext.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractAccessPolicy.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractQueryService.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/contract/ContractAccessPolicyTest.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/contract/ContractQueryServiceTest.java`

**Interfaces:**
- Consumes: `BizContractMapper`、`BusinessActorProvider.current()`、`ContractPermissions.DATA_SCOPE`。
- Produces: `requireReadable(Long)`、`requireOperable(Long)`、`requireFeePlanOperable(Long)`、`requireAttachmentOperable(Long)`。

- [x] **Step 1: 写越权和关联对象失败测试**

```java
@ExtendWith(MockitoExtension.class)
class ContractAccessPolicyTest {
  @Mock BizContractMapper mapper; @Mock BusinessActorProvider actors;
  @Test void inaccessibleContractIsRejected(){
    when(mapper.selectContractById(10L)).thenReturn(contract(10L,"0"));
    when(actors.current()).thenReturn(new BusinessActor(8L,"alice","Alice",3L,false));
    when(mapper.countContractInDataScope(10L,8L,3L,ContractPermissions.DATA_SCOPE)).thenReturn(0);
    ServiceException e=assertThrows(ServiceException.class,()->new ContractAccessPolicy(mapper,actors).requireOperable(10L));
    assertEquals("ACCESS_DENIED",e.getBusinessCode());
  }
  @Test void feePlanResolvesAndChecksOwningContract(){
    when(mapper.selectFeePlanById(21L)).thenReturn(feePlan(21L,10L));allowContract(10L);
    assertEquals(10L,new ContractAccessPolicy(mapper,actors).requireFeePlanOperable(21L).contractId());
  }
}
```

`ContractQueryServiceTest` 增加 `contractDetailDelegatesToPolicy`：mock `access.requireReadable(10L)` 返回合同，断言 `query.contract(10L)` 返回同一对象且不直接调用 Mapper 的详情权限计数。

- [x] **Step 2: 运行并确认服务不存在**

Run: `mvn -pl ruoyi-system -am '-Dtest=ContractAccessPolicyTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
Expected: FAIL，缺少 `ContractAccessPolicy` 或 `ContractFeePlanContext`。

- [x] **Step 3: 实现不可变收费计划上下文**

```java
public record ContractFeePlanContext(Long planId,Long contractId,String contractNo,
    String confirmStatus,String invoiceStatus,String contractStatus,
    BigDecimal receivableAmount,BigDecimal receivedAmount) {}
```

- [x] **Step 4: 实现访问策略**

`requireReadable` 校验不存在和 `delFlag='2'`；非管理员调用 `countContractInDataScope`。`requireOperable` 在可读基础上拒绝 `ARCHIVED/VOID/TERMINATED`。收费计划从现有查询 Map 转为 record 后调用所属合同策略；附件先调用 `selectAttachmentContractId`，不存在返回 `DATA_NOT_FOUND`，成功返回所属合同 ID。

`ContractQueryService.contract` 只调用 `access.requireReadable(contractId)`；删除其私有 `requireAccess` 和直接 `SecurityUtils` 详情权限判断。列表/看板范围参数保持现状。

- [x] **Step 5: 运行策略测试**

Run: `mvn -pl ruoyi-system -am '-Dtest=ContractAccessPolicyTest,ContractQueryServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
Expected: PASS。

- [x] **Step 6: 提交**

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractAccessPolicy.java ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractFeePlanContext.java ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractQueryService.java ruoyi-system/src/test/java/com/ruoyi/system/service/contract/ContractAccessPolicyTest.java ruoyi-system/src/test/java/com/ruoyi/system/service/contract/ContractQueryServiceTest.java
git commit -m "refactor: centralize contract object access policy"
```

### Task 3: 建立可回传主键的合同动作日志

**Files:**
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractStatusLogRecord.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractActionLogService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizContractMapper.java`
- Modify: `ruoyi-system/src/main/resources/mapper/system/BizContractMapper.xml`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/contract/ContractActionLogServiceTest.java`
- Modify: existing contract service tests that mock `insertStatusLog`.

**Interfaces:**
- Produces: `Long ContractActionLogService.record(Long,String,String,String,String,BusinessActor)`。
- Mapper: `int insertStatusLog(ContractStatusLogRecord record)`，MyBatis 回填 `record.logId`。

- [x] **Step 1: 写主键回传失败测试**

```java
@Test void recordReturnsGeneratedLogId(){
  when(mapper.insertStatusLog(any())).thenAnswer(i->{i.<ContractStatusLogRecord>getArgument(0).setLogId(91L);return 1;});
  Long id=service.record(10L,"0","1","submit","提交审批",actor());
  assertEquals(91L,id);
}
@Test void missingGeneratedIdRollsBack(){
  when(mapper.insertStatusLog(any())).thenReturn(1);
  ServiceException e=assertThrows(ServiceException.class,()->service.record(10L,null,"1","submit","提交审批",actor()));
  assertEquals("CONCURRENT_MODIFICATION",e.getBusinessCode());
}
```

- [x] **Step 2: 运行并确认旧 Mapper 签名不匹配**

Run: `mvn -pl ruoyi-system -am '-Dtest=ContractActionLogServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
Expected: FAIL，`insertStatusLog(ContractStatusLogRecord)` 不存在。

- [x] **Step 3: 实现记录和 Mapper**

`ContractStatusLogRecord` 字段为 `logId/contractId/fromStatus/toStatus/actionType/content/createBy`。XML 使用：

```xml
<insert id="insertStatusLog" parameterType="com.ruoyi.system.service.contract.ContractStatusLogRecord"
        useGeneratedKeys="true" keyProperty="logId" keyColumn="log_id">
  insert into biz_contract_status_log(contract_id,from_status,to_status,action_type,content,create_by,create_time)
  values(#{contractId},#{fromStatus},#{toStatus},#{actionType},#{content},#{createBy},sysdate())
</insert>
```

- [x] **Step 4: 让现有服务通过日志服务写记录**

将合同命令、生命周期、收费计划、附件、收款和开票中的直接 `insertStatusLog(...)` 替换为 `ContractActionLogService.record(...)`；本任务先保持返回 ID 未被事件使用，后续任务接入稳定键。

- [x] **Step 5: 运行合同服务测试**

Run: `mvn -pl ruoyi-system -am '-Dtest=ContractActionLogServiceTest,ContractCommandServiceTest,ContractLifecycleServiceTest,ContractFeePlanServiceTest,ContractPaymentServiceTest,ContractInvoiceServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
Expected: PASS。

- [x] **Step 6: 提交**

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/contract ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizContractMapper.java ruoyi-system/src/main/resources/mapper/system/BizContractMapper.xml ruoyi-system/src/test/java/com/ruoyi/system/service/contract
git commit -m "refactor: persist stable contract action identities"
```

### Task 4: 强化合同基础命令与导入边界

**Files:**
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractCommandService.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractImportCommand.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractImportService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizContractMapper.java`
- Modify: `ruoyi-system/src/main/resources/mapper/system/BizContractMapper.xml`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizContractServiceImpl.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/contract/ContractImportServiceTest.java`
- Modify: `ruoyi-system/src/test/java/com/ruoyi/system/service/contract/ContractCommandServiceTest.java`

**Interfaces:**
- Consumes: Task 1 DTO、`CustomerAccessPolicy`、Task 2 `ContractAccessPolicy`、Task 3日志服务。
- Produces: `create(ContractCreateCommand)`、`update(ContractUpdateCommand)`、`importContracts(ContractImportCommand)`。

- [x] **Step 1: 写客户策略和并发条件测试**

```java
@Test void createUsesCustomerPolicyAndIgnoresClientStatuses(){
  when(customers.requireOperable(7L)).thenReturn(customer(7L,"甲客户"));
  when(mapper.insertContract(any())).thenAnswer(i->{i.<BizContract>getArgument(0).setContractId(10L);return 1;});
  service.create(createCommand(7L));
  verify(customers).requireOperable(7L);
  verify(mapper).insertContract(argThat(c->ContractStatus.DRAFT.code().equals(c.getContractStatus())&&ContractAuditStatus.PENDING.code().equals(c.getAuditStatus())));
}
@Test void updateUsesExpectedStatuses(){
  when(access.requireOperable(10L)).thenReturn(draftContract());allowCustomer();
  when(mapper.updateContractConditionally(any(),eq("0"),eq("0"),eq("0"))).thenReturn(0);
  assertEquals("CONCURRENT_MODIFICATION",assertThrows(ServiceException.class,()->service.update(updateCommand())).getBusinessCode());
}
@Test void deleteRejectsConcurrentStateChange(){
  when(access.requireOperable(10L)).thenReturn(draftContract());
  when(mapper.deleteContractConditionally(10L,"0","0","alice")).thenReturn(0);
  assertEquals("CONCURRENT_MODIFICATION",assertThrows(ServiceException.class,()->service.delete(new Long[]{10L})).getBusinessCode());
}
```

- [x] **Step 2: 运行并确认新签名不存在**

Run: `mvn -pl ruoyi-system -am '-Dtest=ContractCommandServiceTest,ContractImportServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
Expected: FAIL。

- [x] **Step 3: 实现 DTO 到实体映射和条件更新**

新增 Mapper：

```java
int updateContractConditionally(@Param("contract") BizContract contract,
    @Param("expectedAuditStatus") String expectedAuditStatus,
    @Param("expectedContractStatus") String expectedContractStatus,
    @Param("expectedDelFlag") String expectedDelFlag);
int deleteContractConditionally(@Param("contractId") Long contractId,
    @Param("expectedAuditStatus") String expectedAuditStatus,
    @Param("expectedContractStatus") String expectedContractStatus,
    @Param("updateBy") String updateBy);
```

XML `where` 必须同时匹配 `contract_id/audit_status/contract_status/del_flag`。删除按合同逐条执行条件更新，任一 0 行使整个事务回滚。`ContractCommandService` 只从 DTO 复制可编辑字段；客户名称来自 `CustomerAccessPolicy` 返回值，状态由服务设置。

- [x] **Step 4: 实现导入服务**

`ContractImportCommand` 定义为 `record ContractImportCommand(List<BizContract> rows,boolean updateSupport,String operator)`；Controller 完成 Excel 解析后构造该命令。`ContractImportService` 负责空输入、客户名称精确解析、重名拒绝、合同名重复、updateSupport 和结果计数；复用 `ContractCommandService`，不直接调用 Facade。导入继续保持当前整批事务语义：任一有效行失败，整批回滚。

- [x] **Step 5: 让 Facade 只委托**

移除 `BizContractServiceImpl` 的两个 Mapper 字段、权限字符串、`resolveImportCustomer`、`assertContractAccess` 和导入循环；详情委托 `ContractQueryService.contract`，导入委托 `ContractImportService`。

- [x] **Step 6: 运行测试**

Run: `mvn -pl ruoyi-system -am '-Dtest=ContractCommandServiceTest,ContractImportServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
Expected: PASS。

- [x] **Step 7: 提交**

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/contract ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizContractServiceImpl.java ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizContractMapper.java ruoyi-system/src/main/resources/mapper/system/BizContractMapper.xml ruoyi-system/src/test/java/com/ruoyi/system/service/contract
git commit -m "refactor: isolate contract commands and import workflow"
```

### Task 5: 强类型化编号规则与合同模板

**Files:**
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractNumberService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractTemplateService.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/contract/ContractNumberServiceTest.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/contract/ContractTemplateServiceTest.java`

**Interfaces:**
- Produces: `updateRule(ContractNumberRuleUpdateCommand)`、`create(ContractTemplateCreateCommand)`、`update(ContractTemplateUpdateCommand)`。

- [x] **Step 1: 写强类型与配置不变量测试**

```java
@Test void disablingLastEnabledRuleIsRejected(){
  when(mapper.countOtherEnabledNoRules(3L)).thenReturn(0);
  ServiceException e=assertThrows(ServiceException.class,()->service.updateRule(disabledRule(3L)));
  assertEquals("PRECONDITION_FAILED",e.getBusinessCode());
}
@Test void activeTemplateCannotBeDeleted(){
  when(mapper.selectTemplateById(5L)).thenReturn(Map.of("templateId",5L,"status","0"));
  assertEquals("STATE_CONFLICT",assertThrows(ServiceException.class,()->service.delete(5L)).getBusinessCode());
}
```

- [x] **Step 2: 运行并确认签名不匹配**

Run: `mvn -pl ruoyi-system -am '-Dtest=ContractNumberServiceTest,ContractTemplateServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
Expected: FAIL。

- [x] **Step 3: 实现命令到持久化 Map 的内部转换**

转换只在 Service 内发生，自动写入 `updateBy/createBy`；字典、日期格式、唯一启用规则和启用模板删除限制保留。所有异常使用 `BusinessErrorCode` 对应业务码。

- [x] **Step 4: 运行测试并提交**

Run: `mvn -pl ruoyi-system -am '-Dtest=ContractNumberServiceTest,ContractTemplateServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
Expected: PASS。

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractNumberService.java ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractTemplateService.java ruoyi-system/src/test/java/com/ruoyi/system/service/contract/ContractNumberServiceTest.java ruoyi-system/src/test/java/com/ruoyi/system/service/contract/ContractTemplateServiceTest.java
git commit -m "refactor: type contract rule and template commands"
```

### Task 6: 强类型化收费计划与附件

**Files:**
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractFeePlanService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractAttachmentService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizContractMapper.java`
- Modify: `ruoyi-system/src/main/resources/mapper/system/BizContractMapper.xml`
- Modify: `ruoyi-system/src/test/java/com/ruoyi/system/service/contract/ContractFeePlanServiceTest.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/contract/ContractAttachmentServiceTest.java`

**Interfaces:**
- Consumes: Task 1 DTO、Task 2策略、Task 3日志服务。
- Produces: `create/update` 强类型方法；删除继续使用主键。

- [x] **Step 1: 写跨合同和不可编辑状态测试**

```java
@Test void feePlanUpdateCannotMoveToAnotherContract(){
  when(access.requireFeePlanOperable(21L)).thenReturn(context(21L,10L,"0","0","1"));
  ContractFeePlanUpdateCommand c=feeUpdate(21L,99L);
  service.update(c);
  verify(mapper).updateFeePlan(argThat(m->Long.valueOf(10L).equals(m.get("contractId"))));
}
@Test void attachmentUsesOwningContractPolicy(){
  service.create(attachment(10L));
  verify(access).requireOperable(10L);
}
```

- [x] **Step 2: 运行并确认旧 Map API 失败**

Run: `mvn -pl ruoyi-system -am '-Dtest=ContractFeePlanServiceTest,ContractAttachmentServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
Expected: FAIL。

- [x] **Step 3: 实现强类型服务**

创建时服务强制 `receivedAmount=0`、确认状态为待确认、开票状态为未开票；更新时使用 `ContractFeePlanContext.contractId()` 覆盖客户端合同 ID，并使用上下文中的三个期望状态做条件更新。附件创建和删除统一调用策略，终态合同由策略拒绝；附件删除 Mapper 增加 `expectedContractStatus`，通过 `exists` 子查询保证父合同仍处于读取时状态。

- [x] **Step 4: 运行测试并提交**

Run: `mvn -pl ruoyi-system -am '-Dtest=ContractFeePlanServiceTest,ContractAttachmentServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
Expected: PASS。

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractFeePlanService.java ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractAttachmentService.java ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizContractMapper.java ruoyi-system/src/main/resources/mapper/system/BizContractMapper.xml ruoyi-system/src/test/java/com/ruoyi/system/service/contract/ContractFeePlanServiceTest.java ruoyi-system/src/test/java/com/ruoyi/system/service/contract/ContractAttachmentServiceTest.java
git commit -m "refactor: secure contract fee plans and attachments"
```

### Task 7: 稳定合同提交、审批与签署事件

**Files:**
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractLifecycleService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizContractMapper.java`
- Modify: `ruoyi-system/src/main/resources/mapper/system/BizContractMapper.xml`
- Modify: `ruoyi-system/src/test/java/com/ruoyi/system/service/contract/ContractLifecycleServiceTest.java`

**Interfaces:**
- Consumes: `ContractAccessPolicy`、`ContractActionLogService`、已有审批表生成主键。
- Produces: 无 UUID 的现有合同事件。

- [x] **Step 1: 写稳定事件键和重复动作测试**

```java
@Test void submitUsesPersistedLogId(){
  allowDraft();when(mapper.updateAuditStatus(anyLong(),anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(1);
  when(logs.record(eq(10L),any(),any(),eq("submit"),any(),any())).thenReturn(91L);
  service.submit(10L);
  verify(events).publish(argThat(e->"CONTRACT_SUBMITTED:10:91".equals(e.getIdempotencyKey())&&Integer.valueOf(1).equals(e.getPayload().get("schemaVersion"))));
}
@Test void approvalUsesGeneratedApprovalId(){
  when(mapper.insertApproval(any())).thenAnswer(i->{i.<Map<String,Object>>getArgument(0).put("approvalId",72L);return 1;});
  approvePass();
  verify(events).publish(argThat(e->"CONTRACT_APPROVED:10:72".equals(e.getIdempotencyKey())));
}
```

- [x] **Step 2: 运行并确认随机键使测试失败**

Run: `mvn -pl ruoyi-system -am '-Dtest=ContractLifecycleServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
Expected: FAIL，实际幂等键含 UUID 或缺少动作 ID。

- [x] **Step 3: 实现稳定事件发布**

提交先条件更新，再取 `logId`，最后发布；审批插入审批记录后必须确认 `approvalId` 已回填；签署附件使用 Mapper 真实字段 `fileType="SIGNED_CONTRACT"`，插入后读取 `attachmentId`，状态日志返回 `logId`。载荷统一包含 `schemaVersion=1/operatorId/logId|approvalId/attachmentId`。

- [x] **Step 4: 去除生命周期中的随机 UUID**

删除 `IdUtils` 依赖；`publish` 接受完整 `idempotencyKey`。若动作记录主键为空，抛 `CONCURRENT_MODIFICATION`，不发布事件。

- [x] **Step 5: 运行测试并提交**

Run: `mvn -pl ruoyi-system -am '-Dtest=ContractLifecycleServiceTest,ContractTodoFlowTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
Expected: PASS。

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractLifecycleService.java ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizContractMapper.java ruoyi-system/src/main/resources/mapper/system/BizContractMapper.xml ruoyi-system/src/test/java/com/ruoyi/system/service/contract/ContractLifecycleServiceTest.java
git commit -m "refactor: stabilize contract lifecycle events"
```

### Task 8: 稳定收款、驳回与开票事件

**Files:**
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractPaymentService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractInvoiceService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/finance/FinanceCommandService.java`
- Modify: `ruoyi-system/src/test/java/com/ruoyi/system/service/contract/ContractPaymentServiceTest.java`
- Modify: `ruoyi-system/src/test/java/com/ruoyi/system/service/contract/ContractInvoiceServiceTest.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/finance/FinanceCommandServiceTest.java`

**Interfaces:**
- Produces: `PAYMENT_CONFIRMED:{contractId}:{planId}:{logId}`、`PAYMENT_REJECTED:{contractId}:{planId}:{logId}`、`INVOICE_HANDLED:{contractId}:{planId}:{logId}`。

- [x] **Step 1: 写稳定键和双入口复用测试**

```java
@Test void paymentUsesPlanAndPersistedLogIdentity(){
  allowPlan();when(mapper.updateFeePlanStatus(any())).thenReturn(1);when(logs.record(anyLong(),any(),any(),eq("fee_confirm"),any(),any())).thenReturn(93L);
  service.confirm(21L,"100.00",null,"bank",null,actor());
  verify(events).publish(argThat(e->"PAYMENT_CONFIRMED:10:21:93".equals(e.getIdempotencyKey())));
}
@Test void financeDelegatesToSamePaymentService(){
  finance.confirmPayment(paymentCommand(21L,"100.00"));
  verify(contractService).confirmFeePlan(argThat(c->c.getPlanId()==21L&&"100.00".equals(c.getReceivedAmount())&&"bank".equals(c.getPaymentMethod())));
}
```

- [x] **Step 2: 运行并确认 UUID 测试失败**

Run: `mvn -pl ruoyi-system -am '-Dtest=ContractPaymentServiceTest,ContractInvoiceServiceTest,FinanceCommandServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
Expected: FAIL。

- [x] **Step 3: 使用上下文、日志 ID 和统一载荷**

收款与开票先调用 `ContractAccessPolicy.requireFeePlanOperable`，再以 record 中状态作为条件更新期望值。动作日志成功后构造稳定键；付款凭证使用 Mapper 真实字段 `fileType="PAYMENT_PROOF"`，附件 ID 写入载荷。删除 `IdUtils`。`FinanceCommandService` 将 `PaymentConfirmCommand/PaymentRejectCommand/InvoiceHandleCommand` 映射为合同侧 `FeeConfirmCommand/FeeRejectCommand/FeeInvoiceCommand` 后调用 `IBizContractService`，不得复制金额、状态或字典校验。

- [x] **Step 4: 验证重复与部分收款语义**

补充：已收齐再次确认返回 `DUPLICATE_OPERATION`；部分收款允许下一次补齐；条件更新 0 行返回 `CONCURRENT_MODIFICATION`；任何失败都不发布事件。

- [x] **Step 5: 运行测试并提交**

Run: `mvn -pl ruoyi-system -am '-Dtest=ContractPaymentServiceTest,ContractInvoiceServiceTest,FinanceCommandServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
Expected: PASS。

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractPaymentService.java ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractInvoiceService.java ruoyi-system/src/main/java/com/ruoyi/system/service/finance/FinanceCommandService.java ruoyi-system/src/test/java/com/ruoyi/system/service/contract ruoyi-system/src/test/java/com/ruoyi/system/service/finance
git commit -m "refactor: stabilize contract payment and invoice events"
```

### Task 9: DTO 化 Controller 与兼容接口

**Files:**
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/IBizContractService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizContractServiceImpl.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/contract/BizContractController.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/contract/BizContractControllerValidationTest.java`
- Modify: `ruoyi-ui/src/api/contract/index.js`

**Interfaces:**
- Consumes: Tasks 1、4、5、6 的强类型方法。
- Produces: 原 URL/JSON 兼容的 `@Valid @RequestBody` 接口。

- [x] **Step 1: 写 MockMvc 兼容测试**

```java
@Test void feePlanKeepsUrlAndCurrentJsonFields() throws Exception {
  mvc.perform(post("/contract/fee").contentType(APPLICATION_JSON)
      .content("{\"contractId\":10,\"periodNo\":1,\"receivableAmount\":1000,\"planReceiveDate\":\"2026-08-01\"}"))
      .andExpect(status().isOk());
  verify(service).insertFeePlan(argThat(c->c.getContractId()==10L&&c.getPeriodNo()==1));
}
@Test void invalidTemplateIsRejectedBeforeService() throws Exception {
  mvc.perform(post("/contract/template").contentType(APPLICATION_JSON).content("{}"))
      .andExpect(status().isBadRequest());
  verifyNoInteractions(service);
}
```

- [x] **Step 2: 运行并确认旧 Map 接口不满足测试**

Run: `mvn -pl ruoyi-admin -am '-Dtest=BizContractControllerValidationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
Expected: FAIL。

- [x] **Step 3: 替换全部合同写 Map 和持久化实体入参**

合同新增/修改、审批、签署、归档/作废/终止、编号规则、模板、收费计划、确认收款、驳回、开票和附件全部使用 Task 1 的强类型命令；导入使用 Task 4 `ContractImportCommand`；接口、Facade 和 Controller 同步改签名。提交/删除等仅含路径主键的动作继续使用 `Long`/`Long[]`。移除 `confirmFeePlan`、`invoiceFeePlan` 等标量重载。查询 `@RequestParam Map` 保留。

- [x] **Step 4: 扫描写接口**

Run: `rg -n '@RequestBody\s+(Map<String,\s*Object>|BizContract)' ruoyi-admin/src/main/java/com/ruoyi/web/controller/contract/BizContractController.java`
Expected: 无输出。

- [x] **Step 5: 补前端 JSDoc 并运行测试**

只为 `addContract/updateContract/updateRule/addTemplate/updateTemplate/addFee/updateFee/addAttachment` 添加字段类型说明，不改 URL 和请求体。

Run: `mvn -pl ruoyi-admin -am '-Dtest=BizContractControllerValidationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
Expected: PASS。

- [x] **Step 6: 提交**

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/IBizContractService.java ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizContractServiceImpl.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/contract/BizContractController.java ruoyi-admin/src/test/java/com/ruoyi/web/controller/contract/BizContractControllerValidationTest.java ruoyi-ui/src/api/contract/index.js
git commit -m "refactor: enforce typed contract api commands"
```

### Task 10: 增加合同到缴费集成安全网与 Facade 守卫

**Files:**
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/integration/CustomerContractPaymentFlowTest.java`
- Modify: `ruoyi-system/src/test/java/com/ruoyi/system/service/LeadCustomerFacadeGuardTest.java`
- Create: `ruoyi-ui/e2e/contract-finance-foundation.spec.js`

**Interfaces:**
- Consumes: Tasks 1-9。
- Produces: 当前销售链路“客户→合同→审批→签署→缴费”的基础治理准入门禁。

- [x] **Step 1: 写业务链路集成测试**

使用真实合同子服务和 mocked persistence edges 顺序执行：客户校验 → 创建合同 → 提交 → 审批通过 → 签署 → 确认收款。断言合同事实和事件键：

```java
assertEquals(List.of(
  "CONTRACT_SUBMITTED:10:91",
  "CONTRACT_APPROVED:10:72",
  "CONTRACT_SIGNED:10:92",
  "PAYMENT_CONFIRMED:10:21:93"),events.keys());
```

再次提交、审批、签署或收齐后确认不得新增动作记录或事件。Outbox publisher 抛异常时，事务测试必须证明业务更新和动作记录回滚。

- [x] **Step 2: 扩展结构守卫**

```java
assertTrue(Files.readAllLines(path("BizContractServiceImpl.java")).size()<=150);
assertFalse(Arrays.stream(BizContractServiceImpl.class.getDeclaredFields())
    .anyMatch(f->BizContractMapper.class.isAssignableFrom(f.getType())||BizCustomerMapper.class.isAssignableFrom(f.getType())));
```

- [x] **Step 3: 写 Playwright 页面/API 回归**

覆盖：从客户页进入合同新增、合同提交、审批、签署、合同收费确认，以及财务页确认同一收费计划。断言仍调用 `/contract`、`/contract/submit/{id}`、`/contract/approval`、`/contract/sign`、`/contract/fee/confirm` 和 `/finance/payment/confirm`，字段名不变。

- [x] **Step 4: 运行目标门禁**

Run: `mvn -pl ruoyi-system -am '-Dtest=CustomerContractPaymentFlowTest,LeadCustomerFacadeGuardTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
Expected: PASS。
Run: `npm --prefix ruoyi-ui run test:e2e -- contract-finance-foundation.spec.js`
Expected: PASS。

- [x] **Step 5: 提交**

```powershell
git add ruoyi-system/src/test/java/com/ruoyi/system/integration/CustomerContractPaymentFlowTest.java ruoyi-system/src/test/java/com/ruoyi/system/service/LeadCustomerFacadeGuardTest.java ruoyi-ui/e2e/contract-finance-foundation.spec.js
git commit -m "test: cover customer contract payment foundation flow"
```

### Task 11: 全量验证、范围审计与文档回填

**Files:**
- Modify: `doc/v0.2-prd-readiness-gap-analysis.md`
- Modify: `docs/superpowers/plans/2026-07-15-contract-finance-foundation.md`

**Interfaces:**
- Produces: 可审计的本子项目完成证据和“缴费→转案→案管接收”下一入口。

- [x] **Step 1: 运行后端全量验证**

Run: `mvn --batch-mode --no-transfer-progress clean verify`
Expected: `BUILD SUCCESS`，所有可执行测试 0 failures/0 errors。

- [x] **Step 2: 运行前端门禁**

Run: `npm --prefix ruoyi-ui run test:todo`
Expected: `todo ui contract ok`。
Run from `ruoyi-ui`: `node node_modules/@vue/cli-service/bin/vue-cli-service.js build`
Expected: exit 0；既有包体积 warning 可记录但不得有编译错误。
Run: `npm --prefix ruoyi-ui run test:e2e`
Expected: 全部 E2E 通过。

- [x] **Step 3: 运行数据库迁移测试**

Run: `mvn -pl ruoyi-admin -am '-Dtest=FlywayMigrationTest,PhaseTwoDatabaseInvariantTest,TodoPhaseTwoTransactionTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
Expected: CI 测试库中 PASS；本机未配置 `TODO_MIGRATION_DB_URL` 时必须如实记录 skipped，不能宣称数据库测试已执行。

- [x] **Step 4: 审计范围和结构**

Run:

```powershell
rg -n 'IdUtils|fastUUID|randomUUID' ruoyi-system/src/main/java/com/ruoyi/system/service/contract
rg -n '@RequestBody\s+(Map<String,\s*Object>|BizContract)' ruoyi-admin/src/main/java/com/ruoyi/web/controller/contract/BizContractController.java
git diff (git merge-base HEAD V0.17)..HEAD --name-only | rg -i 'todo|template.*sql|migration|\.sql$'
```

Expected: 合同事件无随机 ID；合同写接口无 Map/实体入参；没有 v0.2 新表、模板或迁移。

- [x] **Step 5: 回填计划和就绪度文档**

记录 Facade 行数、稳定事件键、单元/集成/E2E 数量、全量命令结果和迁移测试是否真实执行。只标记“客户→合同→审批→签署→缴费基础治理”完成，不标记整个 v0.2 或下一转案子项目完成。

- [x] **Step 6: 提交**

```powershell
git add doc/v0.2-prd-readiness-gap-analysis.md docs/superpowers/plans/2026-07-15-contract-finance-foundation.md
git commit -m "docs: close contract finance foundation governance"
```

### 执行结果（2026-07-15）

| 证据 | 结果 |
|---|---|
| 合同 Facade | `BizContractServiceImpl` 物理 145 行，无 `BizContractMapper/BizCustomerMapper` 字段 |
| 强类型边界 | 合同 Controller、`IBizContractService` 和 Facade 写入口无 `Map<String,Object>` 或 `BizContract` 请求对象 |
| 稳定事件键 | 提交/签署绑定 `logId`，审批绑定 `approvalId`，收款/驳回/开票绑定 `contractId + planId + logId`；合同服务无随机 ID 工具 |
| 后端安全网 | Surefire 报告合计 210 条，207 条实际执行，0 failure、0 error；数据库迁移 3 条因本机未配置测试库跳过 |
| 集成链路 | `CustomerContractPaymentFlowTest` 覆盖提交→审批→签署→缴费稳定事件顺序、重复操作和 Outbox 失败事务回滚 |
| 前端安全网 | Todo UI 契约通过；生产构建成功；Playwright 7/7，通过客户新建合同、审批、签署、合同收费和财务收款 |
| 页面治理 | 修复合同页历史混合编码损坏并重新接回资源/生命周期组件和操作 mixin，主页面 623 行；恢复财务应收、回款、开票、费用表格 |
| 范围约束 | 相对 `V0.17` 未新增 Todo、模板 SQL、数据库迁移或 v0.2 业务页面 |

本计划的本地实现与验证步骤已完成；CI 测试库仍需真实执行 `FlywayMigrationTest`、`PhaseTwoDatabaseInvariantTest`、`TodoPhaseTwoTransactionTest`，因此不能把本地 skipped 记为数据库门禁通过。本计划完成仅表示“客户→合同→审批→签署→缴费”基础治理完成，不表示整个 v0.2 或“缴费→转案→案管接收”已完成。

## Completion Gate

只有以下证据全部成立，本计划才可声明完成：

- `BizContractServiceImpl` ≤150 行且无 `BizContractMapper/BizCustomerMapper` 字段；
- 合同所有写 Controller 和 `IBizContractService` 方法使用强类型命令；
- 合同、收费计划和附件写操作通过 `ContractAccessPolicy`；
- 合同创建/更新复用 `CustomerAccessPolicy`；
- 提交、审批、签署、收费计划、收款和开票使用条件更新；
- 合同现有事件键不含 UUID，并绑定 `log_id/approval_id`；
- 重复或并发请求不产生重复审批、收款、附件或 Outbox；
- 业务事实、动作记录、附件和 Outbox 事务一致；
- 合同页面与财务页面只复用一份收款/开票规则；
- 原 URL、权限、JSON 字段和响应兼容；
- Maven 全量、前端 Todo 契约、生产构建、全部 E2E 和 CI 数据库门禁通过；
- 变更中不存在任何 v0.2 新业务功能。
