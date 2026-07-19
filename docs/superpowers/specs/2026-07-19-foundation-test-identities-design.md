# v0.2 Foundation 业务角色与测试身份设计

> 状态：方案 1 已于 2026-07-19 分两部分确认。正式角色属于所有环境的稳定运行资源；测试身份只允许进入本地和自动化测试环境，不能替代真实业务责任或独立评审证据。

## 1. 目标

为当前 `v0.2-Foundation` 提供一套可重复、可审计且不会污染生产数据的角色与测试身份基线，使线索、案管、律师、财务、准入决策、安全评审、架构/DBA 评审和 G-07 验收流程可以使用不同身份执行权限与独立性测试。

本切片必须同时满足：

- 正式治理角色通过新的前向 Flyway 迁移创建；
- 测试用户只在 `local` 或 `test` Profile 下、且显式开启时创建；
- 生产环境永远不自动创建测试部门或测试用户；
- 密码和 BCrypt Hash 不进入仓库、日志或迁移 SQL；
- 测试身份不能被描述为真实 Owner、真实 Reviewer 或正式准入签字；
- 不创建、合并或选择受 Q-003 阻断的执行角色命名。

## 2. 仓库事实与边界

仓库已存在或已确认六个阶段一业务角色：

| 角色键 | 名称 | 来源 |
|---|---|---|
| `sales` | 销售人员 | `V0_20_24` |
| `case_manager` | 案管员 | v0.15 案件基线 |
| `finance_manager` | 财务人员 | v0.15 财务基线 |
| `law_partner_manager` | 合伙人/法务经理 | v0.15 案件基线 |
| `lawyer` | 律师 | v0.15 案件基线 |
| `intern_lawyer` | 实习律师 | v0.15 案件基线 |

G-02 另有五个执行角色键处于 `CONFLICTING`，必须继续等待 Q-003：

- `enforcement_primary_assistant`；
- `enforcement_secondary_assistant`；
- `execution_manager`；
- `execution_assistant_l1`；
- `execution_assistant_l2`。

本设计不触碰这些角色，不提升其来源状态，也不关闭 Q-003。

## 3. 方案选择

采用“正式治理角色 Flyway + 环境受控应用 Seeder”。

未采用以下方案：

1. **仓库内独立测试 SQL**：需要保存固定 BCrypt Hash，容易被误执行到生产库，并与用户表列顺序耦合。
2. **启动后调用用户管理 API**：依赖管理员 Token、服务启动顺序和网络，CI 不稳定且会扩大秘密管理面。
3. **把测试用户加入正式 Flyway**：无法满足生产零测试身份约束，也会使测试结果与正式准入证据混淆。

## 4. 正式治理角色

新增 `V0_20_28__foundation_governance_roles.sql`。该迁移只创建角色和最小菜单权限关系，不创建部门、用户、用户角色、决策、证据或审批动作。

| 角色键 | 名称 | 权限 |
|---|---|---|
| `foundation_product_owner` | Foundation产品负责人 | `todo:decision:view`、`todo:decision:edit`、`todo:admission:view`、`todo:admission:edit` |
| `foundation_security_reviewer` | Foundation安全评审人 | `todo:admission:view`、`todo:admission:edit` |
| `foundation_arch_dba_reviewer` | Foundation架构/DBA评审人 | `todo:admission:view`、`todo:admission:edit`、`todo:admission:export` |
| `foundation_qa_acceptor` | Foundation QA验收人 | `todo:admission:view`、`todo:admission:edit` |
| `foundation_independent_reviewer` | Foundation独立准入评审人 | `todo:admission:view`、`todo:admission:edit` |

角色使用独立、稳定的 `role_key`，状态为正常，删除标志为未删除。权限绑定包含进入 `todo/config/index` 所需的现有菜单祖先和表中列出的按钮权限，不授予模板发布、系统用户管理、角色管理或任何无关业务权限。

迁移幂等地复用完全相同的现有角色键，但不得覆盖同键异义角色。真实数据库验证必须把角色名称、状态、数据范围和权限集合与本设计逐项比较。迁移不自动把新角色授予 `admin`、`ry` 或其他现有用户。

## 5. 测试组织与十二个人物身份

测试组织树使用一个根部门和五个子部门，共六条测试部门记录：

```text
Foundation测试律所
├── Foundation测试销售部
├── Foundation测试案管部
├── Foundation测试综法部
├── Foundation测试财务部
└── Foundation测试治理组
```

v0.15 基线的 `sys_dept` 没有 `remark` 列，因此六条部门记录不新增备注字段。测试部门使用设计中列出的六个唯一 `dept_code`，并同时要求 `create_by='foundation-test-seeder'`，作为幂等复用、冲突拒绝和环境清理的双重稳定标记。该取舍由用户于 2026-07-19 选择方案 1 确认，不新增 `V0_20_29` 或 `sys_dept.remark`。

十二个账号如下：

| 用户名 | 显示名 | 部门 | 角色 |
|---|---|---|---|
| `ft_product_owner` | 测试产品负责人 | Foundation测试治理组 | `foundation_product_owner` |
| `ft_sales` | 测试销售人员 | Foundation测试销售部 | `sales` |
| `ft_case_manager` | 测试案管员 | Foundation测试案管部 | `case_manager` |
| `ft_partner_manager` | 测试合伙人/法务经理 | Foundation测试综法部 | `law_partner_manager` |
| `ft_lawyer_l1` | 测试一级律师 | Foundation测试综法部 | `lawyer` |
| `ft_lawyer_l2` | 测试二级律师 | Foundation测试综法部 | `lawyer` |
| `ft_intern_lawyer` | 测试实习律师 | Foundation测试综法部 | `intern_lawyer` |
| `ft_finance` | 测试财务人员 | Foundation测试财务部 | `finance_manager` |
| `ft_security_reviewer` | 测试安全评审人 | Foundation测试治理组 | `foundation_security_reviewer` |
| `ft_arch_dba` | 测试架构/DBA评审人 | Foundation测试治理组 | `foundation_arch_dba_reviewer` |
| `ft_qa_acceptor` | 测试QA验收人 | Foundation测试治理组 | `foundation_qa_acceptor` |
| `ft_independent_reviewer` | 测试独立准入评审人 | Foundation测试治理组 | `foundation_independent_reviewer` |

一级律师和二级律师复用同一个稳定角色键；层级是案件分派事实，不新增未经 PRD 冻结的角色键。

所有测试用户使用：

- `user_type='99'`；
- `status='0'`；
- `del_flag='0'`；
- 空手机号、空邮箱和空头像；
- 备注前缀 `FOUNDATION_TEST_IDENTITY|DO_NOT_USE_FOR_PRODUCTION_EVIDENCE`；
- `create_by='foundation-test-seeder'`。

## 6. 环境与密码保护

Seeder 同时受 Profile 和属性双门禁：

```text
active profile 包含 local 或 test
AND foundation.test-identities.enabled=true
AND active profile 不包含 prod
```

配置来源：

```text
FOUNDATION_TEST_IDENTITIES_ENABLED=false
FOUNDATION_TEST_USER_PASSWORD=<required when enabled>
```

默认值必须保持关闭。检测到 `prod` Profile 时，即使开关为真也拒绝执行。密码缺失、短于 12 位，或等于仓库禁止的常见弱口令时拒绝执行。禁止清单至少包含 `123456`、`admin123`、`password`、`12345678` 和基线演示密码。

Seeder 通过现有 `BCryptPasswordEncoder` 生成 Hash，禁止打印原始密码、Hash、连接信息或完整配置。首次创建用户时写入 Hash；重复运行不因 BCrypt 随机盐重写已有密码。密码轮换继续使用现有用户管理能力，不在本切片增加密码回显、导出或复制接口。

## 7. Seeder 数据流与事务

新增聚焦的应用组件 `FoundationTestIdentitySeeder`，只负责环境校验和调用 `FoundationTestIdentityProvisioningService`。Provisioning Service 负责事务内装载：

1. 校验所有十一项正式角色键存在、启用且未删除；其中包括六个业务角色与五个治理角色；
2. 校验五个 Q-003 冲突角色没有被本切片创建或自动绑定；
3. 查找或创建测试组织根和五个子部门；
4. 按稳定用户名查找或创建十二个测试用户；
5. 创建时 BCrypt 加密密码并写入测试标记；
6. 校验或修复测试用户的唯一目标部门和唯一目标角色关系；
7. 完成后只记录新增、复用和修复数量，不记录密码或个人敏感字段。

整个装载过程使用一个事务。任一校验或写入失败时，部门、用户和关系全部回滚。

## 8. 幂等与冲突处理

- 相同用户名已存在且 `user_type='99'`、备注含测试标记时，复用该用户；
- 相同用户名属于真实用户时返回稳定冲突，绝不改名、删除或覆盖；
- 测试部门同名但 `dept_code` 不属于六个预留代码，或者预留 `dept_code` 已被非 `foundation-test-seeder` 数据占用时返回冲突；
- 测试用户存在多余角色时，只移除该测试用户的多余关系，不修改任何真实用户；
- 正式角色缺失、停用、删除或权限不符时失败，不在 Seeder 中创建或扩权；
- 重复运行的最终部门、用户和关系快照必须完全相同；
- 测试用户删除后再次显式运行可以重新创建，但不会恢复任何历史审批动作。

稳定错误至少包括：

```text
FOUNDATION_TEST_IDENTITIES_PROFILE_FORBIDDEN
FOUNDATION_TEST_IDENTITIES_PASSWORD_REQUIRED
FOUNDATION_TEST_IDENTITIES_PASSWORD_WEAK
FOUNDATION_TEST_IDENTITIES_ROLE_MISSING
FOUNDATION_TEST_IDENTITIES_ROLE_MISMATCH
FOUNDATION_TEST_IDENTITIES_USER_CONFLICT
FOUNDATION_TEST_IDENTITIES_DEPARTMENT_CONFLICT
```

## 9. 前端行为

复用现有系统用户和角色管理页面，不新增测试身份专用管理页。

用户列表和详情在 `userType === '99'` 时显示“测试身份”标签；普通用户不显示。页面不得显示、下载或复制测试密码。角色页面通过现有能力显示五个治理角色及其权限。

前端标签只是环境识别信息，不改变登录、权限或准入状态。生产环境没有 Seeder 数据，因此不会出现任何测试身份记录。

## 10. 准入证据边界

测试身份可用于本地和 CI 验证：

- Owner 与 Reviewer 必须不同；
- 不同角色只能访问其授权 API 和页面；
- G-01 决策、G-05 安全评审、G-06 财务评审和 G-07 验收状态机行为；
- 乐观锁、幂等动作和审批守卫。

它们不能作为生产准入事实。仓库文档、准入报告和真实环境证据不得把 `user_type='99'` 的动作计为真实签字，也不得仅因测试数据库达到 8/8 而把生产状态描述为 `ADMITTED`。

## 11. 测试与验证

所有新增行为执行 RED/GREEN TDD：

1. **迁移契约**：恰好五个新治理角色、精确权限集合、零 `sys_user`/`sys_user_role` 写入、零 Q-003 角色写入；
2. **配置门禁**：默认关闭，`local/test` 显式启用才运行，任意包含 `prod` 的 Profile 均拒绝；
3. **密码策略**：缺失和弱口令拒绝，Hash 可验证且不等于明文，日志不包含密码；
4. **冲突与事务**：真实同名用户、真实同名部门、缺失角色和权限不符全部回滚；
5. **幂等**：首次创建、第二次复用、角色/部门关系修复后快照稳定；
6. **真实 MySQL**：从精确 v0.15 十一文件基线执行 Flyway 至 `0.20.28`，显式运行 Seeder 后得到六个测试部门、十二个用户和十二条目标用户角色关系；
7. **生产不变式**：Flyway 后、Seeder 未启用时，`ft_%` 用户和 `FOUNDATION_TEST_IDENTITY` 部门均为零；
8. **权限集**：五个治理角色只有设计列出的权限及访问配置页面所需祖先菜单；
9. **前端**：用户类型 99 显示标签，普通用户不显示；契约、UTF-8、生产构建和 Playwright 通过；
10. **全量回归**：Maven、Flyway、MySQL E2E、前端契约、构建和完整 Playwright 全部重新运行。

## 12. 完成定义

本切片完成时：

- `V0_20_28` 只增加五个正式治理角色及最小权限；
- 本地/测试环境可显式、幂等创建完整十二人物身份；
- 生产路径自动创建测试用户的数量为零；
- 密码不进入仓库或日志；
- 测试身份在前端清晰可辨；
- Owner/Reviewer 分离和各准入流程具备可复现测试身份；
- G-01～G-07 的真实业务结论、Reviewer 和签字状态保持原事实，不因本切片自动提升；
- Foundation 总体准入只依据真实运行环境和真实人员证据重新计算。
