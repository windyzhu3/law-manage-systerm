# 线索→客户基础治理实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不增加 v0.2 新业务功能、不破坏现有接口的前提下，将现有线索与客户模块改造成职责清晰、强类型、事务一致、幂等且具有自动化安全网的销售链路基础模块。

**Architecture:** 保留 `IBizLeadService`、`IBizCustomerService` 和现有 Controller URL 作为兼容边界，原实现类降级为 Facade，业务行为拆入 `service.lead` 与 `service.customer` 子服务。业务表负责事实状态，所有现有线索事件经事务型 Outbox 发布；写接口使用 `law-business` 中的强类型命令对象。

**Tech Stack:** Java 17、Spring Boot 3、Jakarta Validation、MyBatis、MySQL 8、JUnit 5、Mockito、Vue 2、Element UI、Playwright、GitHub Actions。

## Global Constraints

- 不新增首联结果、疑似无效复核、T0/T+1/T+2、无效分级、5天实质进展、Dead-Pool等 v0.2 业务。
- 保持所有现有 Controller URL、权限编码、JSON字段及 AjaxResult 响应结构兼容。
- 生产代码必须由先失败的自动化测试驱动。
- 业务事件必须与业务变更处于同一事务，事件幂等键不得包含随机 UUID。
- 查询接口允许 Map 过滤参数；所有客户写接口不得再接收 `Map<String,Object>`。
- 不修改已发布 Todo 模板版本，不新增 v0.2 Todo 模板。

---

### Task 1: 建立线索与客户访问策略

**Files:**
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadAccessPolicy.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/customer/CustomerAccessPolicy.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/support/BusinessFixtures.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadAccessPolicyTest.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/customer/CustomerAccessPolicyTest.java`

**Interfaces:**
- Consumes: `BizLeadMapper.countLeadInDataScope(...)`、`BizCustomerMapper.countCustomerInDataScope(...)`、`BusinessActorProvider.current()`。
- Produces: `BizLead requireReadable(Long, boolean, boolean)`、`BizLead requireOperable(Long)`、`BizCustomer requireReadable(Long)`、`BizCustomer requireOperable(Long)`。

测试统一静态导入以下夹具，避免各任务自行猜测状态和操作者构造方式：

```java
public final class BusinessFixtures {
  public static BusinessActor actor(){return new BusinessActor(8L,"alice","Alice",3L,false);}
  public static BizLead lead(Long id,String status,String delFlag){BizLead v=new BizLead();v.setLeadId(id);v.setStatus(status);v.setDelFlag(delFlag);return v;}
  public static BizCustomer customer(Long id){BizCustomer v=new BizCustomer();v.setCustomerId(id);v.setStatus("0");v.setDelFlag("0");return v;}
  private BusinessFixtures(){}
}
```

- [ ] **Step 1: 写线索访问策略失败测试**

```java
@ExtendWith(MockitoExtension.class)
class LeadAccessPolicyTest {
  @Mock BizLeadMapper mapper;
  @Mock BusinessActorProvider actors;
  @Test void inaccessibleLeadIsRejected() {
    when(mapper.selectLeadById(7L)).thenReturn(BusinessFixtures.lead(7L, "1", "0"));
    when(actors.current()).thenReturn(new BusinessActor(8L,"alice","Alice",3L,false));
    when(mapper.countLeadInDataScope(7L, 8L, 3L, false)).thenReturn(0);
    ServiceException ex=assertThrows(ServiceException.class,
        ()->new LeadAccessPolicy(mapper,actors).requireOperable(7L));
    assertEquals("ACCESS_DENIED",ex.getBusinessCode());
  }
}
```

- [ ] **Step 2: 运行测试并确认因类不存在而失败**

Run: `mvn -pl ruoyi-system -am -Dtest=LeadAccessPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test`  
Expected: FAIL，编译错误包含 `cannot find symbol LeadAccessPolicy`。

- [ ] **Step 3: 实现最小线索访问策略**

```java
@Service
public class LeadAccessPolicy {
  private final BizLeadMapper mapper;
  private final BusinessActorProvider actors;
  public LeadAccessPolicy(BizLeadMapper mapper,BusinessActorProvider actors){this.mapper=mapper;this.actors=actors;}
  public BizLead requireOperable(Long id){return requireReadable(id,false,false);}
  public BizLead requireReadable(Long id,boolean includeDeleted,boolean allowPool){
    BizLead lead=mapper.selectLeadById(id);
    if(lead==null||(!includeDeleted&&"2".equals(lead.getDelFlag())))throw error(BusinessErrorCode.DATA_NOT_FOUND,"线索不存在");
    BusinessActor a=actors.current();
    if(mapper.countLeadInDataScope(id,a.userId(),a.deptId(),includeDeleted)==0&&!(allowPool&&lead.getOwnerId()==null))
      throw error(BusinessErrorCode.ACCESS_DENIED,"无权访问该线索");
    return lead;
  }
  private ServiceException error(BusinessErrorCode code,String msg){return new ServiceException(msg,code.name());}
}
```

- [ ] **Step 4: 为客户补同等失败测试和实现**

`CustomerAccessPolicy.requireOperable(9L)` 必须调用 `countCustomerInDataScope`，计数为 0 时抛出业务码 `ACCESS_DENIED`，客户不存在、已删除或状态为 `2` 时分别拒绝。

- [ ] **Step 5: 运行两个策略测试和现有模块测试**

Run: `mvn -pl ruoyi-system -am -Dtest=LeadAccessPolicyTest,CustomerAccessPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test`  
Expected: PASS。

- [ ] **Step 6: 提交**

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadAccessPolicy.java ruoyi-system/src/main/java/com/ruoyi/system/service/customer/CustomerAccessPolicy.java ruoyi-system/src/test/java/com/ruoyi/system/support/BusinessFixtures.java ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadAccessPolicyTest.java ruoyi-system/src/test/java/com/ruoyi/system/service/customer/CustomerAccessPolicyTest.java
git commit -m "refactor: centralize lead customer access policies"
```

### Task 2: 拆分线索查询与基础命令

**Files:**
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadQueryService.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadCommandService.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadCommandServiceTest.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizLeadServiceImpl.java`

**Interfaces:**
- Consumes: Task 1 `LeadAccessPolicy`。
- Produces: `LeadQueryService.list/detail/dashboard/settings`，`LeadCommandService.create/update/softDelete/restore/purge/settings`。

- [ ] **Step 1: 写新增和非法删除失败测试**

```java
@ExtendWith(MockitoExtension.class)
class LeadCommandServiceTest {
  @Mock BizLeadMapper mapper; @Mock ISysDictDataService dicts;
  @Mock BusinessEventPublisher events; @Mock BusinessActorProvider actors;
  @Mock LeadAccessPolicy access;
  @Test void createPublishesStableEventKey(){
    BizLead lead=new BizLead(); lead.setLeadName("张三"); lead.setSource("web");
    when(actors.current()).thenReturn(BusinessFixtures.actor()); when(mapper.insertLead(lead)).thenAnswer(i->{lead.setLeadId(11L);return 1;});
    new LeadCommandService(mapper,dicts,events,actors,access).create(lead);
    verify(events).publish(argThat(e->e.idempotencyKey().equals("LEAD_CREATED:11")));
  }
  @Test void convertedLeadCannotBeDeleted(){
    BizLead lead=new BizLead();lead.setLeadId(11L);lead.setStatus(LeadStatus.CONVERTED.code());lead.setDelFlag("0");
    when(access.requireOperable(11L)).thenReturn(lead);
    LeadCommandService service=new LeadCommandService(mapper,dicts,events,actors,access);
    ServiceException ex=assertThrows(ServiceException.class,()->service.softDelete(new Long[]{11L}));
    assertEquals("STATE_CONFLICT",ex.getBusinessCode());
  }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `mvn -pl ruoyi-system -am -Dtest=LeadCommandServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`  
Expected: FAIL，`LeadCommandService` 尚不存在。

- [ ] **Step 3: 迁移查询和基础写入逻辑**

将 `selectLeadList/selectLeadById/selectDashboard/selectSettingList` 移至 `LeadQueryService`；将 `insertLead/updateLead/softDeleteLead/restoreLead/purgeLead` 及设置写操作移至 `LeadCommandService`。事件命令必须使用：

```java
new BusinessEventCommand(BusinessEventType.LEAD_CREATED,"LEAD",lead.getLeadId(),lead.getLeadNo(),
    "LEAD_CREATED:"+lead.getLeadId(),Map.of("schemaVersion",1,"ownerId",ownerId))
```

- [ ] **Step 4: 将 Facade 改为构造器委托**

`BizLeadServiceImpl` 的对应接口方法只保留 `return query.list(lead)`、`return commands.create(lead)` 形式，不复制校验逻辑。

- [ ] **Step 5: 运行测试**

Run: `mvn -pl ruoyi-system -am -Dtest=LeadCommandServiceTest,LeadFirstContactValidatorTest,LeadFirstContactHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`  
Expected: PASS。

- [ ] **Step 6: 提交**

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/lead ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizLeadServiceImpl.java ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadCommandServiceTest.java
git commit -m "refactor: extract lead query and command services"
```

### Task 3: 拆分线索分配与公海服务

**Files:**
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadAssignmentService.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadPoolService.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadAssignmentServiceTest.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadPoolServiceTest.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizLeadMapper.java`
- Modify: `ruoyi-system/src/main/resources/mapper/system/BizLeadMapper.xml`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizLeadServiceImpl.java`

**Interfaces:**
- Consumes: Task 1 `LeadAccessPolicy`。
- Produces: `assign(Long,Long,String)`、`moveToPool(Long,String)`、`claim(Long)`，并返回受影响行数。

- [ ] **Step 1: 写状态条件和稳定事件键测试**

```java
@Test void assigningLeadUsesConditionalUpdateAndLogIdentity(){
  BizLead lead=lead(7L,LeadStatus.UNASSIGNED.code());
  when(access.requireOperable(7L)).thenReturn(lead);
  when(mapper.assignLead(7L,9L,3L,"alice",LeadStatus.UNASSIGNED.code())).thenReturn(1);
  when(mapper.insertAssignmentLog(anyLong(),isNull(),eq(9L),eq("assign"),any(),eq("alice"))).thenReturn(1);
  when(mapper.selectLatestAssignmentLogId(7L)).thenReturn(21L);
  service.assign(7L,9L,"首次分配");
  verify(events).publish(argThat(e->e.idempotencyKey().equals("LEAD_ASSIGNED:7:21")));
}
```

- [ ] **Step 2: 运行并确认因新Mapper签名不存在而失败**

Run: `mvn -pl ruoyi-system -am -Dtest=LeadAssignmentServiceTest,LeadPoolServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`  
Expected: FAIL，缺少条件更新参数或服务类。

- [ ] **Step 3: 增加条件更新与日志ID读取**

```java
int assignLead(Long leadId,Long ownerId,Long deptId,String updateBy,String expectedStatus);
int moveToPool(Long leadId,String reason,String updateBy,String expectedStatus);
int claimLead(Long leadId,Long ownerId,Long deptId,String updateBy,String expectedStatus);
Long selectLatestAssignmentLogId(Long leadId);
```

XML更新语句必须包含 `and status = #{expectedStatus} and del_flag = '0'`；领取还必须包含 `and owner_id is null`。

- [ ] **Step 4: 实现分配、公海、领取服务**

状态条件失败抛 `CONCURRENT_MODIFICATION`；记录日志成功后以日志 ID 构造事件幂等键；事件载荷统一包含 `schemaVersion=1` 和 `operatorId`。

- [ ] **Step 5: 运行测试并提交**

Run: `mvn -pl ruoyi-system -am -Dtest=LeadAssignmentServiceTest,LeadPoolServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`  
Expected: PASS。

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/lead ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizLeadMapper.java ruoyi-system/src/main/resources/mapper/system/BizLeadMapper.xml ruoyi-system/src/test/java/com/ruoyi/system/service/lead
git commit -m "refactor: isolate lead assignment and pool workflows"
```

### Task 4: 拆分线索跟进与转客户事务

**Files:**
- Create: `law-business/src/main/java/com/law/business/lead/dto/LeadFollowupCommand.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadFollowupService.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadConversionService.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadFollowupServiceTest.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadConversionServiceTest.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizLeadMapper.java`
- Modify: `ruoyi-system/src/main/resources/mapper/system/BizLeadMapper.xml`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizLeadServiceImpl.java`

**Interfaces:**
- Consumes: 现有 `IBizCustomerService.convertLeadToCustomer(BizLead)`；Task 6完成后改为直接依赖 `CustomerCommandService.createFromLead(BizLead)`。
- Produces: `add/update/remove followup`、`Long convert(Long leadId)`。

- [ ] **Step 1: 写重复转化和稳定事件测试**

```java
@Test void convertedLeadReturnsExistingCustomerWithoutWriting(){
  BizLead lead=lead(7L,LeadStatus.CONVERTED.code());lead.setCustomerId(31L);
  when(access.requireOperable(7L)).thenReturn(lead);
  assertEquals(31L,service.convert(7L));
  verify(customers,never()).convertLeadToCustomer(any());
}
@Test void conversionPublishesOneStableEvent(){
  BizLead lead=lead(7L,LeadStatus.FOLLOWING.code());
  BizCustomer converted=BusinessFixtures.customer(31L);
  when(access.requireOperable(7L)).thenReturn(lead);when(customers.convertLeadToCustomer(lead)).thenReturn(converted);
  when(mapper.bindCustomerConditionally(7L,31L,"alice",LeadStatus.FOLLOWING.code())).thenReturn(1);
  assertEquals(31L,service.convert(7L));
  verify(events).publish(argThat(e->e.idempotencyKey().equals("LEAD_CONVERTED:7:31")));
}
```

- [ ] **Step 2: 运行并确认失败**

Run: `mvn -pl ruoyi-system -am -Dtest=LeadFollowupServiceTest,LeadConversionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`  
Expected: FAIL，服务或条件绑定方法不存在。

- [ ] **Step 3: 增加条件绑定并实现事务服务**

```java
int bindCustomerConditionally(Long leadId,Long customerId,String updateBy,String expectedStatus);

@Transactional
public Long convert(Long leadId) {
  BizLead lead=access.requireOperable(leadId);
  if(LeadStatus.CONVERTED.code().equals(lead.getStatus())&&lead.getCustomerId()!=null)return lead.getCustomerId();
  requireConvertible(lead);
  Long customerId=customers.convertLeadToCustomer(lead).getCustomerId();
  changed(mapper.bindCustomerConditionally(leadId,customerId,actor.userName(),lead.getStatus()));
  events.publish(convertedEvent(lead,customerId,actor));
  return customerId;
}
```

- [ ] **Step 4: 实现跟进服务和DTO校验**

`LeadFollowupCommand` 使用 `@NotNull leadId`、`@NotBlank followType/followResult/content`、`@Size(max=1000) content`；Controller 的 JSON 字段保持不变。

- [ ] **Step 5: 验证并提交**

Run: `mvn -pl ruoyi-system -am -Dtest=LeadFollowupServiceTest,LeadConversionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`  
Expected: PASS。

```powershell
git add law-business/src/main/java/com/law/business/lead/dto/LeadFollowupCommand.java ruoyi-system/src/main/java/com/ruoyi/system/service/lead ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizLeadMapper.java ruoyi-system/src/main/resources/mapper/system/BizLeadMapper.xml ruoyi-system/src/test/java/com/ruoyi/system/service/lead
git commit -m "refactor: secure lead followup and conversion transactions"
```

### Task 5: 建立客户写入命令契约

**Files:**
- Create: `law-business/src/main/java/com/law/business/customer/dto/CustomerContactCreateCommand.java`
- Create: `law-business/src/main/java/com/law/business/customer/dto/CustomerContactUpdateCommand.java`
- Create: `law-business/src/main/java/com/law/business/customer/dto/CustomerFollowupCreateCommand.java`
- Create: `law-business/src/main/java/com/law/business/customer/dto/CustomerTagCreateCommand.java`
- Create: `law-business/src/main/java/com/law/business/customer/dto/CustomerTagUpdateCommand.java`
- Create: `law-business/src/main/java/com/law/business/customer/dto/CustomerTagAssignCommand.java`
- Create: `law-business/src/main/java/com/law/business/customer/dto/CustomerMergeCommand.java`
- Create: `law-business/src/test/java/com/law/business/customer/dto/CustomerCommandValidationTest.java`

**Interfaces:**
- Produces: Jakarta Validation命令对象，字段名与现有前端 JSON 一致。

- [ ] **Step 1: 写 Bean Validation 失败测试**

```java
class CustomerCommandValidationTest {
  Validator validator=Validation.buildDefaultValidatorFactory().getValidator();
  @Test void contactRequiresCustomerNameAndMobile(){
    CustomerContactCreateCommand c=new CustomerContactCreateCommand();
    Set<String> fields=validator.validate(c).stream().map(v->v.getPropertyPath().toString()).collect(toSet());
    assertTrue(fields.containsAll(Set.of("customerId","contactName","mobile")));
  }
  @Test void mergeRejectsMissingCustomers(){
    CustomerMergeCommand c=new CustomerMergeCommand();
    assertEquals(Set.of("mainCustomerId","mergedCustomerId"),fields(validator.validate(c)));
  }
}
```

- [ ] **Step 2: 运行并确认类不存在**

Run: `mvn -pl law-business -Dtest=CustomerCommandValidationTest test`  
Expected: FAIL，命令类不存在。

- [ ] **Step 3: 实现命令对象**

联系人命令定义 `customerId/contactId/contactName/mobile/email/position/mainFlag/remark`；跟进定义 `customerId/followType/followTime/content/nextFollowTime`；标签定义 `tagId/tagName/tagColor/status`；分配定义 `customerId/Long[] tagIds`；合并定义 `mainCustomerId/mergedCustomerId/content`。使用 `@NotNull`、`@NotBlank`、`@Size`、`@Email` 和 `@Pattern`。

- [ ] **Step 4: 运行测试并提交**

Run: `mvn -pl law-business -Dtest=CustomerCommandValidationTest test`  
Expected: PASS。

```powershell
git add law-business/src/main/java/com/law/business/customer law-business/src/test/java/com/law/business/customer
git commit -m "refactor: define typed customer write commands"
```

### Task 6: 拆分客户查询与基础命令

**Files:**
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/customer/CustomerQueryService.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/customer/CustomerCommandService.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/customer/CustomerCommandServiceTest.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizCustomerServiceImpl.java`

**Interfaces:**
- Consumes: Task 1 `CustomerAccessPolicy`。
- Produces: `list/detail/dashboard/import/create/update/delete` 和 `Long createFromLead(BizLead)`。

- [ ] **Step 1: 写重复客户与线索转客户测试**

```java
@Test void duplicateMobileIsRejected(){
  BizCustomer input=customer("13800000000",null,"张三");
  when(mapper.selectDuplicateCustomerInScope(eq("13800000000"),isNull(),eq("张三"),anyLong(),anyLong(),eq(true),anyString()))
      .thenReturn(customer("13800000000",null,"既有客户"));
  ServiceException ex=assertThrows(ServiceException.class,()->service.create(input));
  assertEquals("PRECONDITION_FAILED",ex.getBusinessCode());
}
@Test void createFromLeadCreatesPrimaryContact(){
  BizLead lead=lead(7L);when(mapper.insertCustomer(any())).thenReturn(1);when(mapper.insertContact(any())).thenReturn(1);
  Long id=service.createFromLead(lead);
  verify(mapper).insertContact(argThat(m->"Y".equals(m.get("mainFlag"))));
  assertNotNull(id);
}
```

- [ ] **Step 2: 运行并确认失败**

Run: `mvn -pl ruoyi-system -am -Dtest=CustomerCommandServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`  
Expected: FAIL，服务不存在。

- [ ] **Step 3: 迁移查询和基础命令**

将客户列表、详情、工作台迁入 `CustomerQueryService`；将CRUD、导入、重复校验和 `createFromLead` 迁入 `CustomerCommandService`。`createFromLead` 只创建客户与主联系人，不更新线索、不发布线索事件，由 Task 4 的 `LeadConversionService` 负责事务编排。

- [ ] **Step 4: 移除临时转换适配器并直接注入CustomerCommandService**

更新 `LeadConversionService` 构造器，使其依赖 `CustomerCommandService`；再次运行 Task 4 测试。

- [ ] **Step 5: 验证并提交**

Run: `mvn -pl ruoyi-system -am -Dtest=CustomerCommandServiceTest,LeadConversionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`  
Expected: PASS。

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/customer ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadConversionService.java ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizCustomerServiceImpl.java ruoyi-system/src/test/java/com/ruoyi/system/service/customer ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadConversionServiceTest.java
git commit -m "refactor: extract customer query and command services"
```

### Task 7: 拆分客户联系人、跟进与标签服务

**Files:**
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/customer/CustomerContactService.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/customer/CustomerFollowupService.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/customer/CustomerTagService.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/customer/CustomerContactServiceTest.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/customer/CustomerFollowupServiceTest.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/customer/CustomerTagServiceTest.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/IBizCustomerService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizCustomerServiceImpl.java`

**Interfaces:**
- Consumes: Task 5命令对象、Task 1 `CustomerAccessPolicy`。
- Produces: 强类型 `create/update` 方法；查询仍返回现有 Map 结果。

- [ ] **Step 1: 写跨客户联系人修改失败测试**

```java
@Test void contactCannotBeMovedToAnotherCustomer(){
  when(mapper.selectContactCustomerId(5L)).thenReturn(10L);
  CustomerContactUpdateCommand c=command(5L,11L);
  ServiceException ex=assertThrows(ServiceException.class,()->service.update(c));
  assertEquals("ACCESS_DENIED",ex.getBusinessCode());
  verify(mapper,never()).updateContact(any());
}
```

- [ ] **Step 2: 写标签集合幂等和跟进权限测试**

标签分配必须校验每个标签启用、去重 tagIds，并仅在集合变化时重写关系；客户跟进必须先调用 `access.requireOperable(customerId)`。

- [ ] **Step 3: 运行并确认失败**

Run: `mvn -pl ruoyi-system -am -Dtest=CustomerContactServiceTest,CustomerFollowupServiceTest,CustomerTagServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`  
Expected: FAIL，服务类不存在。

- [ ] **Step 4: 实现三个服务并让Facade委托**

Mapper可继续使用 Map 持久化，但 Map 只能在Service内部由DTO转换；Controller和接口层不再暴露写Map。

- [ ] **Step 5: 验证并提交**

Run: `mvn -pl ruoyi-system -am -Dtest=CustomerContactServiceTest,CustomerFollowupServiceTest,CustomerTagServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`  
Expected: PASS。

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/customer ruoyi-system/src/main/java/com/ruoyi/system/service/IBizCustomerService.java ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizCustomerServiceImpl.java ruoyi-system/src/test/java/com/ruoyi/system/service/customer
git commit -m "refactor: isolate customer contact followup and tag services"
```

### Task 8: 拆分客户合并事务

**Files:**
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/customer/CustomerMergeService.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/customer/CustomerMergeServiceTest.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizCustomerMapper.java`
- Modify: `ruoyi-system/src/main/resources/mapper/system/BizCustomerMapper.xml`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizCustomerServiceImpl.java`

**Interfaces:**
- Consumes: `CustomerMergeCommand`、`CustomerAccessPolicy`。
- Produces: `@Transactional int merge(CustomerMergeCommand)`。

- [ ] **Step 1: 写同客户、越权和并发失败测试**

```java
@Test void sameCustomerCannotBeMerged(){
  CustomerMergeCommand c=new CustomerMergeCommand();c.setMainCustomerId(7L);c.setMergedCustomerId(7L);
  ServiceException ex=assertThrows(ServiceException.class,()->service.merge(c));
  assertEquals("VALIDATION_FAILED",ex.getBusinessCode());
}
@Test void concurrentMergeIsRejected(){
  allow(7L);allow(8L);when(mapper.markMergedConditionally(8L,"alice","0")).thenReturn(0);
  ServiceException ex=assertThrows(ServiceException.class,()->service.merge(command(7L,8L)));
  assertEquals("CONCURRENT_MODIFICATION",ex.getBusinessCode());
}
```

- [ ] **Step 2: 运行并确认失败**

Run: `mvn -pl ruoyi-system -am -Dtest=CustomerMergeServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`  
Expected: FAIL，服务或条件更新不存在。

- [ ] **Step 3: 实现条件合并**

```java
int markMergedConditionally(Long fromId,String updateBy,String expectedStatus);
```

事务内顺序为：校验两客户 → 条件标记从客户 → 迁移联系人/跟进/标签/合同 → 写合并日志。任何一步行数异常抛出并回滚。

- [ ] **Step 4: 验证并提交**

Run: `mvn -pl ruoyi-system -am -Dtest=CustomerMergeServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`  
Expected: PASS。

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/customer/CustomerMergeService.java ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizCustomerMapper.java ruoyi-system/src/main/resources/mapper/system/BizCustomerMapper.xml ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizCustomerServiceImpl.java ruoyi-system/src/test/java/com/ruoyi/system/service/customer/CustomerMergeServiceTest.java
git commit -m "refactor: make customer merge transactional and concurrent safe"
```

### Task 9: DTO化Controller并验证API兼容

**Files:**
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/lead/BizLeadController.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/customer/BizCustomerController.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/lead/BizLeadControllerValidationTest.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/customer/BizCustomerControllerValidationTest.java`
- Modify: `ruoyi-ui/src/api/lead/index.js`
- Modify: `ruoyi-ui/src/api/customer/index.js`

**Interfaces:**
- Consumes: Tasks 4/5/7/8 DTO和Service方法。
- Produces: 保持原URL与JSON字段的 `@Valid @RequestBody` 接口。

- [ ] **Step 1: 写MockMvc兼容测试**

```java
@WebMvcTest(BizCustomerController.class)
class BizCustomerControllerValidationTest {
  @Test void mergeKeepsUrlAndValidatesBody() throws Exception {
    mvc.perform(post("/customer/merge").contentType(APPLICATION_JSON)
      .content("{\"mainCustomerId\":7,\"mergedCustomerId\":8,\"content\":\"重复客户\"}"))
      .andExpect(status().isOk());
    verify(service).mergeCustomer(argThat(c->c.getMainCustomerId()==7L&&c.getMergedCustomerId()==8L));
  }
  @Test void invalidContactIsRejectedBeforeService() throws Exception {
    mvc.perform(post("/customer/contact").contentType(APPLICATION_JSON).content("{}"))
      .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }
}
```

- [ ] **Step 2: 运行并确认旧Map接口不满足测试**

Run: `mvn -pl ruoyi-admin -am -Dtest=BizLeadControllerValidationTest,BizCustomerControllerValidationTest -Dsurefire.failIfNoSpecifiedTests=false test`  
Expected: FAIL，Service签名或参数校验结果不匹配。

- [ ] **Step 3: 替换所有客户写Map参数**

使用 `@Valid` 命令对象；删除 Controller 的 `requiredLong`、`text`；标签分配将路径中的 customerId 写入 `CustomerTagAssignCommand` 后调用Service。前端请求函数保持请求体字段不变，只补充明确JSDoc类型。

- [ ] **Step 4: 扫描并验证无写Map**

Run: `rg -n '@(Post|Put)Mapping[\s\S]{0,180}@RequestBody Map<String, Object>' ruoyi-admin/src/main/java/com/ruoyi/web/controller/customer/BizCustomerController.java`  
Expected: 无输出。

- [ ] **Step 5: 运行测试与前端构建并提交**

Run: `mvn -pl ruoyi-admin -am -Dtest=BizLeadControllerValidationTest,BizCustomerControllerValidationTest -Dsurefire.failIfNoSpecifiedTests=false test`  
Expected: PASS。  
Run: `npm --prefix ruoyi-ui run build:prod`  
Expected: exit code 0。

```powershell
git add ruoyi-admin/src/main/java/com/ruoyi/web/controller/lead/BizLeadController.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/customer/BizCustomerController.java ruoyi-admin/src/test/java/com/ruoyi/web/controller ruoyi-ui/src/api/lead/index.js ruoyi-ui/src/api/customer/index.js
git commit -m "refactor: enforce typed lead customer api commands"
```

### Task 10: 增加销售链路集成测试与Facade边界守卫

**Files:**
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/integration/LeadCustomerConversionFlowTest.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/LeadCustomerFacadeGuardTest.java`
- Create: `ruoyi-ui/e2e/lead-customer-foundation.spec.js`
- Modify: `ruoyi-ui/playwright.config.js`
- Modify: `ruoyi-ui/package.json`

**Interfaces:**
- Consumes: Tasks 1-9全部行为。
- Produces: 线索→客户基础链路自动化准入门禁。

- [ ] **Step 1: 写事务集成测试**

使用 Spring 测试事务和测试数据库验证：创建线索 → 分配 → 跟进 → 转客户后，客户、主联系人、线索 `customer_id/status`、`LEAD_CONVERTED` Outbox 各一条；重复转化仍各一条。故意让Outbox插入失败时断言客户、联系人和线索更新全部回滚。

- [ ] **Step 2: 写Facade结构守卫**

```java
@Test void facadesStayBelowCompatibilityBoundary() throws Exception {
  assertTrue(Files.readAllLines(path("BizLeadServiceImpl.java")).size()<=150);
  assertTrue(Files.readAllLines(path("BizCustomerServiceImpl.java")).size()<=150);
}
```

同时使用反射断言 `BizLeadServiceImpl` 和 `BizCustomerServiceImpl` 没有直接字段依赖 `BizLeadMapper`/`BizCustomerMapper`，确保它们只委托子服务。

- [ ] **Step 3: 写Playwright现有页面回归**

覆盖全部线索页面的分配、公海领取、跟进、转客户，以及客户详情中主联系人、标签和合并入口。网络请求断言继续使用现有 `/lead/*`、`/customer/*` URL和字段。

- [ ] **Step 4: 运行目标测试并修复暴露的回归**

Run: `mvn -pl ruoyi-system -am -Dtest=LeadCustomerConversionFlowTest,LeadCustomerFacadeGuardTest -Dsurefire.failIfNoSpecifiedTests=false test`  
Expected: PASS。  
Run: `npm --prefix ruoyi-ui run test:e2e -- lead-customer-foundation.spec.js`  
Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add ruoyi-system/src/test/java/com/ruoyi/system/integration/LeadCustomerConversionFlowTest.java ruoyi-system/src/test/java/com/ruoyi/system/service/LeadCustomerFacadeGuardTest.java ruoyi-ui/e2e/lead-customer-foundation.spec.js ruoyi-ui/playwright.config.js ruoyi-ui/package.json
git commit -m "test: cover lead to customer foundation flow"
```

### Task 11: 全量质量门禁与文档回填

**Files:**
- Modify: `doc/v0.2-prd-readiness-gap-analysis.md`
- Modify: `docs/superpowers/plans/2026-07-14-lead-customer-foundation.md`

**Interfaces:**
- Produces: 可审计的完成证据和下一模块（客户→合同）入口。

- [ ] **Step 1: 运行后端全量验证**

Run: `mvn --batch-mode --no-transfer-progress clean verify`  
Expected: BUILD SUCCESS，所有模块测试通过。

- [ ] **Step 2: 运行前端质量门禁**

Run: `npm --prefix ruoyi-ui run test:todo`  
Expected: PASS。  
Run: `npm --prefix ruoyi-ui run build:prod`  
Expected: exit code 0。  
Run: `npm --prefix ruoyi-ui run test:e2e`  
Expected: 所有Todo及线索客户用例通过。

- [ ] **Step 3: 运行迁移测试**

Run: `mvn -pl ruoyi-admin -am -Dtest=FlywayMigrationTest,PhaseTwoDatabaseInvariantTest,TodoPhaseTwoTransactionTest -Dsurefire.failIfNoSpecifiedTests=false test`  
Expected: PASS；本轮若无数据库迁移，仍验证既有迁移未受影响。

- [ ] **Step 4: 审计范围与验收标准**

Run: `git diff V0.17~1..HEAD --name-only` 并人工核对没有新增 v0.2 表、页面、事件类型或模板；检查Facade行数、写Map扫描、稳定幂等键和全部测试证据。

- [ ] **Step 5: 回填状态并提交**

在缺口分析中将“线索→客户基础治理”标记为已完成，记录验证命令和结果；在本计划勾选完成任务。

```powershell
git add doc/v0.2-prd-readiness-gap-analysis.md docs/superpowers/plans/2026-07-14-lead-customer-foundation.md
git commit -m "docs: close lead customer foundation governance"
```

## Completion Gate

只有以下证据全部成立，本计划才可声明完成：

- 两个Facade均不直接依赖Mapper且不超过150行；
- 客户Controller所有写接口均使用强类型DTO；
- 线索分配、公海、跟进、转化和客户联系人、跟进、标签、合并均有先失败后通过的测试记录；
- 同一线索重复/并发转化不会产生重复客户、联系人或事件；
- 事件幂等键稳定且业务事务与Outbox一致；
- 现有URL、权限和前端字段兼容；
- Maven全量、前端Todo契约、生产构建、全部E2E和迁移测试通过；
- 变更中不存在任何v0.2新增业务功能。
