# Foundation Test Identities and Governance Roles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不污染生产数据、不伪造准入签署的前提下，为 `v0.2-Foundation` 建立 5 个正式治理角色，以及仅在本地/测试环境显式启用的 6 个测试部门、12 个测试用户和精确角色关系。

**Architecture:** 正式治理角色与最小权限使用前向 Flyway 迁移进入所有环境；测试部门和用户由受 Profile、显式开关及密码策略共同保护的应用 Seeder 创建。`ruoyi-system` 承担目录、密码策略、事务化装载和专用 Mapper，`ruoyi-admin` 承担启动配置与环境门禁，现有系统用户页面只增加可复用的“测试身份”标签。测试身份永远不能改变真实 Foundation 准入事实。

**Tech Stack:** Java 17、Spring Boot 3、Spring Security BCrypt、MyBatis、Flyway、MySQL 8、JUnit 5、Mockito、Vue 2.6、Element UI、Node.js 契约脚本、Playwright、GitHub Actions。

## Global Constraints

- 所有生产代码先写失败测试，再实现最小代码使测试通过；每个任务保留 RED/GREEN 命令及结果。
- 只能新增前向迁移 `V0_20_28__foundation_governance_roles.sql`；不得修改任何已执行迁移。
- Flyway 只创建 5 个正式治理角色和最小菜单关系，不得写入 `sys_dept`、`sys_user`、`sys_user_role`、决策、证据或审批动作。
- 不得创建或绑定 Q-003 阻塞的 5 个执行角色键。
- Seeder 默认关闭，只允许 `local` 或 `test` Profile 且 `foundation.test-identities.enabled=true`；任意活动 Profile 含 `prod` 时必须拒绝启动。
- 密码只从 `FOUNDATION_TEST_USER_PASSWORD` 获取，不得进入源码、SQL、日志、异常、测试快照或前端。
- 既有测试用户重复运行时不得重写密码 Hash；仅修复其目标部门和唯一目标角色关系。
- 真实同名用户、无测试标记的同名部门、缺失/停用/删除/权限不符的正式角色都必须稳定失败并回滚整个装载事务。
- `user_type='99'` 仅表示测试身份，不得被准入聚合、导出或文档视为真实 Owner、Reviewer 或签署人。
- 每个任务完成后先运行其定向测试，再提交；未经用户明确要求不得推送远端。

---

## Task 1: Lock the Governance Migration Contract in RED

**Files:**

- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/FoundationGovernanceRoleMigrationContractTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/FlywayMigrationTest.java`

- [ ] **Step 1: Add a source-level contract test for the new migration**

  创建 `FoundationGovernanceRoleMigrationContractTest`，读取 `/db/migration/V0_20_28__foundation_governance_roles.sql`，断言：

  ```java
  private static final Set<String> GOVERNANCE_ROLE_KEYS = Set.of(
      "foundation_product_owner",
      "foundation_security_reviewer",
      "foundation_arch_dba_reviewer",
      "foundation_qa_acceptor",
      "foundation_independent_reviewer"
  );

  private static final Set<String> FORBIDDEN_Q003_ROLE_KEYS = Set.of(
      "enforcement_primary_assistant",
      "enforcement_secondary_assistant",
      "execution_manager",
      "execution_assistant_l1",
      "execution_assistant_l2"
  );
  ```

  测试还必须拒绝迁移文本出现 `insert into sys_user`、`insert into sys_dept`、`insert into sys_user_role`、`todo_foundation_decision`、`todo_admission_evidence`。

- [ ] **Step 2: Advance the Flyway terminal-version assertion**

  将 `FlywayMigrationTest#migratesV015BaselineToTodoPhaseOneSchema` 的终态版本从 `0.20.27` 改为 `0.20.28`，并新增 `verifyFoundationGovernanceRoles(url)` 调用。

- [ ] **Step 3: Define exact database invariants before implementation**

  `verifyFoundationGovernanceRoles` 必须断言：

  ```java
  assertEquals(5L, count(connection,
      "select count(*) from sys_role where role_key in ("
          + "'foundation_product_owner','foundation_security_reviewer',"
          + "'foundation_arch_dba_reviewer','foundation_qa_acceptor',"
          + "'foundation_independent_reviewer') and status='0' and del_flag='0'"));
  assertEquals(0L, count(connection,
      "select count(*) from sys_user where user_name like 'ft\\_%' escape '\\\\'"));
  assertEquals(0L, count(connection,
      "select count(*) from sys_dept where remark like 'FOUNDATION_TEST_DEPARTMENT%'"));
  assertEquals(0L, count(connection,
      "select count(*) from sys_user_role ur join sys_user u on u.user_id=ur.user_id "
          + "where u.user_name like 'ft\\_%' escape '\\\\'"));
  ```

  对 5 个角色逐一查询按钮权限，精确比较集合，不使用“至少包含”。允许的非按钮菜单只限 `todo/config/index` 及其祖先菜单。

- [ ] **Step 4: Run RED tests**

  Run:

  ```powershell
  mvn -pl ruoyi-admin -am -Dtest=FoundationGovernanceRoleMigrationContractTest,FlywayMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test
  ```

  Expected: `FoundationGovernanceRoleMigrationContractTest` 因迁移文件不存在失败；数据库环境未提供时 `FlywayMigrationTest` 只按既有 assumption 跳过，不能掩盖契约失败。

- [ ] **Step 5: Commit the RED contract**

  ```powershell
  git add ruoyi-admin/src/test/java/com/ruoyi/web/migration/FoundationGovernanceRoleMigrationContractTest.java ruoyi-admin/src/test/java/com/ruoyi/web/migration/FlywayMigrationTest.java
  git commit -m "test(foundation): define governance role migration contract"
  ```

---

## Task 2: Implement the Governance Role Migration

**Files:**

- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_28__foundation_governance_roles.sql`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/FoundationGovernanceRoleMigrationContractTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/FlywayMigrationTest.java`

- [ ] **Step 1: Insert the five roles idempotently without overwriting collisions**

  迁移用 `insert ... select ... where not exists` 创建以下固定定义：

  | role_key | role_name | role_sort | data_scope |
  |---|---|---:|---|
  | `foundation_product_owner` | `Foundation产品负责人` | 40 | `5` |
  | `foundation_security_reviewer` | `Foundation安全评审人` | 41 | `5` |
  | `foundation_arch_dba_reviewer` | `Foundation架构DBA评审人` | 42 | `5` |
  | `foundation_qa_acceptor` | `Foundation QA验收人` | 43 | `5` |
  | `foundation_independent_reviewer` | `Foundation独立准入评审人` | 44 | `5` |

  所有角色使用 `menu_check_strictly=1`、`dept_check_strictly=1`、`status='0'`、`del_flag='0'`、`create_by='flyway-v0.20.28'`。迁移不得更新同键既有角色，数据库 invariant 负责把异义碰撞暴露为失败。

- [ ] **Step 2: Bind exact button permissions and required menu ancestors**

  使用角色键和 `sys_menu.perms` 联结写入 `sys_role_menu`：

  ```text
  foundation_product_owner:
    todo:decision:view
    todo:decision:edit
    todo:admission:view
    todo:admission:edit

  foundation_security_reviewer:
    todo:admission:view
    todo:admission:edit

  foundation_arch_dba_reviewer:
    todo:admission:view
    todo:admission:edit
    todo:admission:export

  foundation_qa_acceptor:
    todo:admission:view
    todo:admission:edit

  foundation_independent_reviewer:
    todo:admission:view
    todo:admission:edit
  ```

  另为每个角色绑定 `component='todo/config/index'` 的菜单及该菜单现有祖先链。不得绑定模板发布、用户管理、角色管理或其他业务权限。

- [ ] **Step 3: Make permission verification exact**

  测试用 `select m.perms ... where m.perms is not null and m.perms<>''` 获取按钮权限并与上表 `Set<String>` 比较；另断言角色拥有配置页菜单且无非祖先额外菜单。

- [ ] **Step 4: Run the migration contract GREEN**

  Run:

  ```powershell
  mvn -pl ruoyi-admin -am -Dtest=FoundationGovernanceRoleMigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test
  ```

  Expected: `BUILD SUCCESS`，5 个角色键全部存在于脚本，禁写表和 Q-003 角色键均不存在。

- [ ] **Step 5: Run against a fresh MySQL baseline**

  复用 `.github/workflows/ci.yml` 的 11 个 v0.15 SQL 文件初始化临时库，设置 `TODO_MIGRATION_DB_URL/USER/PASSWORD` 后运行：

  ```powershell
  mvn -pl ruoyi-admin -am -Dtest=FlywayMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test
  ```

  Expected: Flyway 当前版本 `0.20.28`；5 个正式角色及精确权限通过；`ft_%` 用户、测试部门、测试用户角色关系均为 0。

- [ ] **Step 6: Commit the migration**

  ```powershell
  git add ruoyi-admin/src/main/resources/db/migration/V0_20_28__foundation_governance_roles.sql ruoyi-admin/src/test/java/com/ruoyi/web/migration
  git commit -m "feat(foundation): add governance roles with least privilege"
  ```

---

## Task 3: Add the Test-Identity Domain Catalog and Password Policy

**Files:**

- Create: `ruoyi-system/src/main/java/com/ruoyi/system/foundation/FoundationTestIdentityCatalog.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/foundation/FoundationTestIdentityErrorCode.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/foundation/FoundationTestIdentityException.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/foundation/FoundationTestPasswordPolicy.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/foundation/FoundationTestIdentityCatalogTest.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/foundation/FoundationTestPasswordPolicyTest.java`

- [ ] **Step 1: Write catalog RED tests**

  断言目录精确包含：6 个部门、12 个用户、11 个所需角色、5 个禁止的 Q-003 角色；每个用户只有一个部门代码和一个角色键；用户名、部门代码、显示名无重复。

  稳定部门代码使用：

  | department_code | department_name | parent_code |
  |---|---|---|
  | `FOUNDATION_TEST_FIRM` | `Foundation测试律所` | 无，`parent_id=0` |
  | `FOUNDATION_TEST_SALES` | `Foundation测试销售部` | `FOUNDATION_TEST_FIRM` |
  | `FOUNDATION_TEST_CASE_MANAGEMENT` | `Foundation测试案管部` | `FOUNDATION_TEST_FIRM` |
  | `FOUNDATION_TEST_GENERAL_LAW` | `Foundation测试综法部` | `FOUNDATION_TEST_FIRM` |
  | `FOUNDATION_TEST_FINANCE` | `Foundation测试财务部` | `FOUNDATION_TEST_FIRM` |
  | `FOUNDATION_TEST_GOVERNANCE` | `Foundation测试治理组` | `FOUNDATION_TEST_FIRM` |

- [ ] **Step 2: Write password-policy RED tests**

  覆盖 null、空白、少于 12 位、`123456`、`admin123`、`password`、`12345678`、仓库基线演示密码；覆盖一个 12 位以上强密码通过。比较禁用密码时忽略大小写和首尾空格。

- [ ] **Step 3: Run RED tests**

  ```powershell
  mvn -pl ruoyi-system -am -Dtest=FoundationTestIdentityCatalogTest,FoundationTestPasswordPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test
  ```

  Expected: 编译失败，因为目录和密码策略类尚不存在。

- [ ] **Step 4: Implement the immutable catalog**

  `FoundationTestIdentityCatalog` 提供不可变记录：

  ```java
  public record DepartmentSpec(String code, String name, String parentCode, int orderNum) {}
  public record UserSpec(String userName, String nickName, String departmentCode, String roleKey) {}

  public static final String DEPARTMENT_MARKER =
      "FOUNDATION_TEST_DEPARTMENT|DO_NOT_USE_FOR_PRODUCTION_EVIDENCE";
  public static final String USER_MARKER =
      "FOUNDATION_TEST_IDENTITY|DO_NOT_USE_FOR_PRODUCTION_EVIDENCE";
  public static final String CREATED_BY = "foundation-test-seeder";
  public static final String TEST_USER_TYPE = "99";
  ```

  12 个用户目录必须逐项写死为：

  | user_name | nick_name | department_code | role_key |
  |---|---|---|---|
  | `ft_product_owner` | `测试产品负责人` | `FOUNDATION_TEST_GOVERNANCE` | `foundation_product_owner` |
  | `ft_sales` | `测试销售人员` | `FOUNDATION_TEST_SALES` | `sales` |
  | `ft_case_manager` | `测试案管员` | `FOUNDATION_TEST_CASE_MANAGEMENT` | `case_manager` |
  | `ft_partner_manager` | `测试合伙人法务经理` | `FOUNDATION_TEST_GENERAL_LAW` | `law_partner_manager` |
  | `ft_lawyer_l1` | `测试一级律师` | `FOUNDATION_TEST_GENERAL_LAW` | `lawyer` |
  | `ft_lawyer_l2` | `测试二级律师` | `FOUNDATION_TEST_GENERAL_LAW` | `lawyer` |
  | `ft_intern_lawyer` | `测试实习律师` | `FOUNDATION_TEST_GENERAL_LAW` | `intern_lawyer` |
  | `ft_finance` | `测试财务人员` | `FOUNDATION_TEST_FINANCE` | `finance_manager` |
  | `ft_security_reviewer` | `测试安全评审人` | `FOUNDATION_TEST_GOVERNANCE` | `foundation_security_reviewer` |
  | `ft_arch_dba` | `测试架构DBA评审人` | `FOUNDATION_TEST_GOVERNANCE` | `foundation_arch_dba_reviewer` |
  | `ft_qa_acceptor` | `测试QA验收人` | `FOUNDATION_TEST_GOVERNANCE` | `foundation_qa_acceptor` |
  | `ft_independent_reviewer` | `测试独立准入评审人` | `FOUNDATION_TEST_GOVERNANCE` | `foundation_independent_reviewer` |

  禁止目录中出现任何 Q-003 角色。一级和二级律师复用 `lawyer`，不得为级别另造角色键。

- [ ] **Step 5: Implement stable coded failures and password validation**

  `FoundationTestIdentityException` 持有 `FoundationTestIdentityErrorCode code`，异常消息只包含错误码和非敏感对象键。密码策略失败只抛：

  ```text
  FOUNDATION_TEST_IDENTITIES_PASSWORD_REQUIRED
  FOUNDATION_TEST_IDENTITIES_PASSWORD_WEAK
  ```

  不把原始密码拼入消息。

- [ ] **Step 6: Run GREEN tests and commit**

  ```powershell
  mvn -pl ruoyi-system -am -Dtest=FoundationTestIdentityCatalogTest,FoundationTestPasswordPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test
  git add ruoyi-system/src/main/java/com/ruoyi/system/foundation ruoyi-system/src/test/java/com/ruoyi/system/foundation
  git commit -m "feat(foundation): define test identity catalog and password policy"
  ```

  Expected: `BUILD SUCCESS`，目录数量和映射精确，弱密码全部稳定拒绝。

---

## Task 4: Implement Transactional, Idempotent Provisioning

**Files:**

- Create: `ruoyi-system/src/main/java/com/ruoyi/system/foundation/FoundationTestIdentityMapper.java`
- Create: `ruoyi-system/src/main/resources/mapper/system/FoundationTestIdentityMapper.xml`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/foundation/FoundationTestIdentityProvisioningResult.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/foundation/FoundationTestIdentityProvisioningService.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/foundation/FoundationTestIdentityMapperContractTest.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/foundation/FoundationTestIdentityProvisioningServiceTest.java`
- Modify: `ruoyi-common/src/main/java/com/ruoyi/common/core/domain/entity/SysUser.java`
- Modify: `ruoyi-system/src/main/resources/mapper/system/SysUserMapper.xml`

- [ ] **Step 1: Write RED tests for user-type persistence**

  为 `SysUser` 增加契约测试，要求存在 `userType` getter/setter；`SysUserMapper.xml` 的 result map、列表查询、详情查询和 insert 都必须读写 `user_type`。这一步先只写测试，不改生产类。

- [ ] **Step 2: Write RED mapper contract tests**

  专用 Mapper 必须提供精确接口：

  ```java
  SysRole selectRoleByKey(String roleKey);
  Set<String> selectPermissionKeysByRoleId(Long roleId);
  SysDept selectDepartmentByCode(String deptCode);
  SysDept selectDepartmentByName(String deptName);
  int insertDepartment(SysDept department);
  SysUser selectAnyUserByUserName(String userName);
  int insertUser(SysUser user);
  List<Long> selectRoleIdsByUserId(Long userId);
  int deleteRoleLinksByUserId(Long userId);
  int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
  int updateTestUserPlacement(@Param("userId") Long userId, @Param("deptId") Long deptId);
  ```

  `selectAnyUserByUserName` 必须包含逻辑删除用户，以防 Seeder 借删除态绕过真实用户名碰撞。

- [ ] **Step 3: Write provisioning-service RED tests**

  Mockito 测试至少覆盖：

  - 11 个正式角色均存在、启用、未删除、治理角色权限精确时继续；
  - 角色缺失抛 `ROLE_MISSING`；停用/删除/治理权限不符抛 `ROLE_MISMATCH`；
  - 同名真实用户抛 `USER_CONFLICT`，不执行任何写操作；
  - 同名无标记部门抛 `DEPARTMENT_CONFLICT`；
  - 首次运行创建 6 部门、12 用户、12 关系；
  - 第二次运行不插入用户、不调用密码编码、不改密码；
  - 已标记测试用户的部门和角色关系被修复为精确一个；
  - 结果只包含 `created/reused/repaired` 计数，不包含密码或 Hash。

- [ ] **Step 4: Run RED tests**

  ```powershell
  mvn -pl ruoyi-system -am -Dtest=FoundationTestIdentityMapperContractTest,FoundationTestIdentityProvisioningServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
  ```

  Expected: 新类和 `SysUser.userType` 尚不存在导致失败。

- [ ] **Step 5: Add `userType` to the existing system-user contract**

  在 `SysUser` 增加 `private String userType` 及 getter/setter；在 `SysUserMapper.xml` 的 `SysUserResult`、`selectUserVo`、`selectUserList`、allocated/unallocated 查询和 `insertUser` 中读写 `u.user_type`。不得在更新接口允许普通前端任意改写该标记。

- [ ] **Step 6: Implement the dedicated mapper**

  XML 使用参数绑定，禁止 `${}`。部门按稳定 `dept_code` 查询，同时按名称检查冲突；用户查询不带 `del_flag='0'`。插入部门/用户均使用数据库生成主键，并保留 marker、`create_by`、时间和状态字段。

- [ ] **Step 7: Implement the provisioning transaction**

  `FoundationTestIdentityProvisioningService#provision(String rawPassword)` 使用 `@Transactional(rollbackFor = Exception.class)`，顺序固定：

  1. 密码策略校验；
  2. 验证 11 个角色，治理角色精确比较权限集合；
  3. 验证本服务不创建/绑定 5 个 Q-003 角色；
  4. 父部门先于子部门查找/创建并计算 `ancestors`；
  5. 按用户名查找/创建 12 用户；
  6. 新用户才调用注入的 `BCryptPasswordEncoder.encode`；
  7. 已标记用户修复 `dept_id` 和唯一 `sys_user_role`；
  8. 返回非敏感计数。

  已存在用户必须同时满足 `userType='99'`、remark 以 USER_MARKER 开头、`createBy='foundation-test-seeder'`，否则冲突。

- [ ] **Step 8: Run GREEN tests and commit**

  ```powershell
  mvn -pl ruoyi-system -am -Dtest=FoundationTestIdentityMapperContractTest,FoundationTestIdentityProvisioningServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
  git add ruoyi-common/src/main/java/com/ruoyi/common/core/domain/entity/SysUser.java ruoyi-system/src/main/java/com/ruoyi/system/foundation ruoyi-system/src/main/resources/mapper/system/FoundationTestIdentityMapper.xml ruoyi-system/src/main/resources/mapper/system/SysUserMapper.xml ruoyi-system/src/test/java/com/ruoyi/system/foundation
  git commit -m "feat(foundation): provision isolated test identities transactionally"
  ```

  Expected: `BUILD SUCCESS`；第二次运行的编码器调用次数为 0；所有冲突在首次写入前失败。

---

## Task 5: Add the Profile- and Property-Gated Seeder

**Files:**

- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/foundation/FoundationTestIdentityProperties.java`
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/foundation/FoundationTestIdentitySeeder.java`
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/foundation/FoundationTestIdentityConfiguration.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/foundation/FoundationTestIdentitySeederTest.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/foundation/FoundationTestIdentityConfigurationTest.java`
- Modify: `ruoyi-admin/src/main/resources/application.yml`

- [ ] **Step 1: Write RED configuration tests**

  使用 `ApplicationContextRunner` 断言默认配置不创建 Seeder Bean；只有属性 `foundation.test-identities.enabled=true` 才创建。`FoundationTestIdentityProperties` 只含 `enabled` 和 `password`，不得实现 `toString()` 输出密码。

- [ ] **Step 2: Write RED profile-gate tests**

  直接构造 Seeder，覆盖：

  | active profiles | enabled | expected |
  |---|---:|---|
  | `local` | true | 调用 provision 一次 |
  | `test` | true | 调用 provision 一次 |
  | `local,test` | true | 调用 provision 一次 |
  | `druid` | true | `PROFILE_FORBIDDEN` |
  | `prod` | true | `PROFILE_FORBIDDEN` |
  | `local,prod` | true | `PROFILE_FORBIDDEN` |

  任何失败消息和捕获日志不得包含测试密码。

- [ ] **Step 3: Run RED tests**

  ```powershell
  mvn -pl ruoyi-admin -am -Dtest=FoundationTestIdentitySeederTest,FoundationTestIdentityConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
  ```

  Expected: 类不存在导致编译失败。

- [ ] **Step 4: Implement properties and conditional configuration**

  `application.yml` 增加：

  ```yaml
  foundation:
    test-identities:
      enabled: ${FOUNDATION_TEST_IDENTITIES_ENABLED:false}
      password: ${FOUNDATION_TEST_USER_PASSWORD:}
  ```

  `FoundationTestIdentityConfiguration` 使用：

  ```java
  @Configuration
  @EnableConfigurationProperties(FoundationTestIdentityProperties.class)
  @ConditionalOnProperty(
      prefix = "foundation.test-identities",
      name = "enabled",
      havingValue = "true"
  )
  ```

  不设置 `matchIfMissing=true`。

- [ ] **Step 5: Implement the runtime gate**

  `FoundationTestIdentitySeeder implements ApplicationRunner`。先读取 `Environment#getActiveProfiles()`；若含 `prod`，立即抛 `PROFILE_FORBIDDEN`；否则至少含 `local` 或 `test` 才可调用 provisioning。成功日志仅输出 `created/reused/repaired` 计数。

- [ ] **Step 6: Run GREEN tests and commit**

  ```powershell
  mvn -pl ruoyi-admin -am -Dtest=FoundationTestIdentitySeederTest,FoundationTestIdentityConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
  git add ruoyi-admin/src/main/java/com/ruoyi/web/foundation ruoyi-admin/src/test/java/com/ruoyi/web/foundation ruoyi-admin/src/main/resources/application.yml
  git commit -m "feat(foundation): gate test identity seeding by environment"
  ```

  Expected: 默认不注册、local/test 显式启用运行、所有 prod 组合拒绝，日志断言无密码泄漏。

---

## Task 6: Show Test Identities Safely in the Existing User UI

**Files:**

- Create: `ruoyi-ui/src/components/TestIdentityTag/index.vue`
- Modify: `ruoyi-ui/src/views/system/user/index.vue`
- Modify: `ruoyi-ui/src/views/system/user/view.vue`
- Create: `ruoyi-ui/scripts/check-foundation-test-identity-ui.js`
- Modify: `ruoyi-ui/package.json`
- Modify: `.github/workflows/ci.yml`
- Create: `ruoyi-ui/e2e/foundation-test-identity-tag.spec.js`

- [ ] **Step 1: Write the UI contract script in RED**

  契约脚本断言共享组件：

  ```vue
  <el-tag v-if="userType === '99'" type="warning" size="mini">测试身份</el-tag>
  ```

  并断言用户列表和详情都引用该组件；代码不得包含 `FOUNDATION_TEST_USER_PASSWORD`、密码复制按钮或 Hash 展示。

- [ ] **Step 2: Add the failing npm gate**

  `package.json` 增加：

  ```json
  "test:foundation-identities": "node scripts/check-foundation-test-identity-ui.js"
  ```

  Run:

  ```powershell
  npm --prefix ruoyi-ui run test:foundation-identities
  ```

  Expected: 共享组件不存在，命令失败。

- [ ] **Step 3: Implement the shared tag and integrate both screens**

  `TestIdentityTag` 只接收 `userType` 字符串 prop。用户列表在账号链接后显示；详情在登录账号旁显示。普通 `00` 用户不渲染占位标签。

- [ ] **Step 4: Add a Playwright behavior test**

  Mock `/getInfo`、`/getRouters`、`/system/user/list` 和 `/system/user/{id}`：列表同时返回一个 `userType='99'` 和一个 `userType='00'` 用户。断言页面只出现一次“测试身份”；打开测试用户详情后标签仍存在；页面 DOM 不含 seed password 或 BCrypt `$2a/$2b/$2y` 值。

- [ ] **Step 5: Wire the contract into CI**

  在 frontend job 的 `Verify Foundation CI scope` 后新增：

  ```yaml
  - name: Verify Foundation test identity UI
    run: npm run test:foundation-identities
  ```

  同时更新 `check-foundation-ci.js`，要求 workflow 包含该命令。

- [ ] **Step 6: Run GREEN gates and commit**

  ```powershell
  npm --prefix ruoyi-ui run test:foundation-identities
  npm --prefix ruoyi-ui run test:foundation-ci
  npm --prefix ruoyi-ui run build:prod
  npm --prefix ruoyi-ui run test:e2e -- foundation-test-identity-tag.spec.js
  git add ruoyi-ui/src/components/TestIdentityTag ruoyi-ui/src/views/system/user ruoyi-ui/scripts/check-foundation-test-identity-ui.js ruoyi-ui/e2e/foundation-test-identity-tag.spec.js ruoyi-ui/package.json .github/workflows/ci.yml
  git commit -m "feat(foundation): identify test users in system UI"
  ```

  Expected: 契约、构建和单个 Playwright 用例全部通过；普通用户无标签。

---

## Task 7: Prove Real-MySQL Idempotency, Rollback, and Production Non-Mutation

**Files:**

- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/FoundationTestIdentityEndToEndTest.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/FoundationTestIdentityRollbackTest.java`
- Modify: `.github/workflows/ci.yml`
- Modify: `ruoyi-ui/scripts/check-foundation-ci.js`

- [ ] **Step 1: Write the real-database E2E test in RED**

  在与 `MigrationTestDatabase` 相同的环境变量门禁下启动 Spring 测试上下文，Profile=`test`、开关=true，密码由 CI secret-like env `FOUNDATION_TEST_USER_PASSWORD` 提供。首次运行后断言：

  ```text
  6 marked departments
  12 active, non-deleted user_type=99 users
  12 exact user-role links
  0 extra role links for those users
  0 users bound to any Q-003 role
  12 BCrypt hashes matching the supplied password
  ```

- [ ] **Step 2: Prove idempotency without hash churn**

  保存 12 个 `(user_name,password)` Hash 快照，第二次调用 provisioning；断言部门/用户/关系数量不变且 Hash 逐字节不变。人为增加一个错误角色和错误部门关系后第三次运行，断言只修复目标测试用户且其他真实用户不变。

- [ ] **Step 3: Prove transaction rollback**

  在独立测试事务中制造以下场景并逐个断言装载后数据库快照不变：

  - 删除一个所需角色；
  - 将治理角色权限改为多一个按钮；
  - 预建同名真实用户；
  - 预建同名但无 marker 部门。

  每个场景都验证对应稳定错误码。

- [ ] **Step 4: Prove the production path remains empty**

  仅执行 Flyway、不启用 Seeder 的数据库中断言：

  ```sql
  select count(*) from sys_user where user_name like 'ft\_%' escape '\\'; -- 0
  select count(*) from sys_dept where remark like 'FOUNDATION_TEST_DEPARTMENT%'; -- 0
  select count(*) from sys_user where user_type='99'; -- 0
  ```

  使用 `local,prod` Profile 且开关=true 的上下文必须在 provisioning 前失败，并保持上述三项为 0。

- [ ] **Step 5: Add the tests to the migration CI job**

  在 migration job 的测试步骤前生成一次性随机密码，不把固定密码提交进仓库：

  ```yaml
  - name: Generate ephemeral Foundation test password
    shell: bash
    run: echo "FOUNDATION_TEST_USER_PASSWORD=$(openssl rand -base64 32)" >> "$GITHUB_ENV"
  ```

  将测试列表加入：

  ```text
  FoundationGovernanceRoleMigrationContractTest
  FoundationTestIdentityEndToEndTest
  FoundationTestIdentityRollbackTest
  ```

  此值仅用于隔离的 CI MySQL 服务，不进入迁移或应用默认配置。

- [ ] **Step 6: Run the MySQL gate and commit**

  ```powershell
  mvn -pl ruoyi-admin -am -Dtest=FlywayMigrationTest,FoundationTestIdentityEndToEndTest,FoundationTestIdentityRollbackTest -Dsurefire.failIfNoSpecifiedTests=false test
  git add ruoyi-admin/src/test/java/com/ruoyi/web/migration/FoundationTestIdentityEndToEndTest.java ruoyi-admin/src/test/java/com/ruoyi/web/migration/FoundationTestIdentityRollbackTest.java .github/workflows/ci.yml ruoyi-ui/scripts/check-foundation-ci.js
  git commit -m "test(foundation): verify isolated test identities on mysql"
  ```

  Expected: fresh MySQL 到 `0.20.28`；未启用时 0 测试身份；启用时精确 6/12/12；重复运行 Hash 稳定；冲突全部回滚。

---

## Task 8: Document Operations and Run the Full Foundation Regression

**Files:**

- Create: `doc/foundation/foundation-test-identities-runbook.md`
- Modify: `doc/reviews/v0.2-foundation-admission-report.md`
- Modify: `README.md`

- [ ] **Step 1: Write the operations runbook**

  文档必须给出本地启用方式：

  ```powershell
  $env:SPRING_PROFILES_ACTIVE='local,druid'
  $env:FOUNDATION_TEST_IDENTITIES_ENABLED='true'
  $env:FOUNDATION_TEST_USER_PASSWORD=Read-Host '输入至少12位本地测试密码'
  ```

  同时说明关闭方式、12 个账号对应角色、无密码显示/导出能力、重复运行行为、冲突错误码和清理 marker。文档不得包含一个可直接使用的真实密码。

- [ ] **Step 2: Preserve the admission truth boundary**

  在准入报告中明确：该切片只提供本地/测试身份，不能自动填写真实 Owner/Reviewer、不能关闭 G-01～G-07、不能把 Foundation 状态提升为 `ADMITTED`。保持仓库当前真实准入结论不变。

- [ ] **Step 3: Run all backend tests**

  ```powershell
  mvn --batch-mode --no-transfer-progress clean verify
  ```

  Expected: 全量模块 `BUILD SUCCESS`；Windows symlink capability tests 只允许保留仓库既有的 2 个显式 capability skip，不得新增 skip。

- [ ] **Step 4: Run all frontend gates**

  ```powershell
  npm --prefix ruoyi-ui run test:todo
  npm --prefix ruoyi-ui run test:todo-schema
  npm --prefix ruoyi-ui run test:foundation-ci
  npm --prefix ruoyi-ui run test:foundation-identities
  npm --prefix ruoyi-ui run test:encoding
  npm --prefix ruoyi-ui run build:prod
  npm --prefix ruoyi-ui run test:e2e
  ```

  Expected: 所有 Node 契约通过、生产构建成功、完整 Playwright 套件在既有 34 个用例基础上新增测试身份用例且 0 failure。

- [ ] **Step 5: Re-run the fresh-MySQL quality gate**

  按 CI 的 11 文件基线从空库初始化并运行 migration job 完整测试列表。记录 MySQL 版本、Flyway 终态、测试总数、skip 数、6/12/12 数量和第二次 Hash 不变证据到 runbook 的“验证记录”节。

- [ ] **Step 6: Inspect changes for secret and boundary violations**

  ```powershell
  rg -n "FOUNDATION_TEST_USER_PASSWORD|\$2[aby]\$|123456|admin123|password" --glob '!docs/superpowers/**' --glob '!**/target/**' --glob '!ruoyi-ui/node_modules/**'
  git diff --check
  git status --short
  ```

  Expected: 只出现配置变量名、弱密码拒绝测试和既有基线事实；没有可用 Seeder 密码、固定 BCrypt Hash、尾随空格或意外产物。

- [ ] **Step 7: Commit documentation and verification record**

  ```powershell
  git add doc/foundation/foundation-test-identities-runbook.md doc/reviews/v0.2-foundation-admission-report.md README.md
  git commit -m "docs(foundation): document isolated test identity operations"
  ```

- [ ] **Step 8: Final branch review without pushing**

  ```powershell
  git log --oneline --decorate -10
  git diff 420a2e05..HEAD --stat
  git status --short
  ```

  Expected: 工作区干净；迁移、后端、前端、E2E、CI 和文档提交边界清晰。等待用户明确指示后才能推送。

---

## Completion Evidence Checklist

- [ ] Flyway 终态为 `0.20.28`，只新增 5 个正式治理角色和精确最小权限。
- [ ] 未启用 Seeder 的 fresh MySQL 中测试部门、`ft_%` 用户、`user_type='99'` 用户均为 0。
- [ ] local/test 显式启用后精确产生 6 个测试部门、12 个测试用户、12 条唯一目标角色关系。
- [ ] 任意 prod Profile、弱密码、角色不符、真实用户/部门碰撞均在写入前或事务内稳定失败并回滚。
- [ ] 第二次运行不改 BCrypt Hash，关系修复只作用于已标记测试用户。
- [ ] 用户列表和详情只对 `userType='99'` 显示“测试身份”，无密码或 Hash 展示能力。
- [ ] Maven、Flyway、MySQL E2E、前端契约、UTF-8、生产构建、完整 Playwright 和 CI 定义全部通过。
- [ ] G-01～G-07 与 Foundation 总体准入结论未被测试身份自动提升。
