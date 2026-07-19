# Foundation 测试身份操作手册

> 适用范围：本地开发与自动化测试。本文中的 12 个账号均为隔离测试身份，不能作为生产 Owner、Reviewer、验收人或准入签字人。

## 1. 启用与关闭

默认开关为关闭。只允许在活动 Profile 包含 `local` 或 `test`、不包含 `prod`，并且显式开启开关时执行 Seeder。每次启动前在当前 PowerShell 会话注入密码，不把明文或 BCrypt Hash 写入仓库、配置文件、命令历史模板或日志：

```powershell
$env:SPRING_PROFILES_ACTIVE='local,druid'
$env:FOUNDATION_TEST_IDENTITIES_ENABLED='true'
$env:FOUNDATION_TEST_USER_PASSWORD=Read-Host '输入至少12位本地测试密码'
```

设置后按项目既有方式启动或重启后端。Seeder 是启动期动作；改变环境变量但不重启，不会触发或撤销装载。

关闭后续自动装载并清除当前会话中的秘密：

```powershell
$env:FOUNDATION_TEST_IDENTITIES_ENABLED='false'
Remove-Item Env:FOUNDATION_TEST_USER_PASSWORD -ErrorAction SilentlyContinue
```

关闭开关不会自动删除已经创建的测试记录。任何包含 `prod` 的 Profile（包括 `local,prod` 或 `test,prod`）都会以 `FOUNDATION_TEST_IDENTITIES_PROFILE_FORBIDDEN` 拒绝启动期装载，并且在写入身份数据前失败。

密码必须在运行时提供、去除首尾空白后至少 12 位，并通过弱口令拒绝规则。系统不会记录明文或 Hash；首次创建时使用 BCrypt，重复运行不会因随机盐重写已有 Hash。密码轮换使用既有用户管理能力，不通过 Seeder 或本文保存密码。

## 2. 测试账号与角色

| 用户名 | 显示名 | 测试部门 | 唯一目标角色 |
|---|---|---|---|
| `ft_product_owner` | 测试产品负责人 | Foundation测试治理组 | `foundation_product_owner` |
| `ft_sales` | 测试销售人员 | Foundation测试销售部 | `sales` |
| `ft_case_manager` | 测试案管员 | Foundation测试案管部 | `case_manager` |
| `ft_partner_manager` | 测试合伙人法务经理 | Foundation测试综法部 | `law_partner_manager` |
| `ft_lawyer_l1` | 测试一级律师 | Foundation测试综法部 | `lawyer` |
| `ft_lawyer_l2` | 测试二级律师 | Foundation测试综法部 | `lawyer` |
| `ft_intern_lawyer` | 测试实习律师 | Foundation测试综法部 | `intern_lawyer` |
| `ft_finance` | 测试财务人员 | Foundation测试财务部 | `finance_manager` |
| `ft_security_reviewer` | 测试安全评审人 | Foundation测试治理组 | `foundation_security_reviewer` |
| `ft_arch_dba` | 测试架构DBA评审人 | Foundation测试治理组 | `foundation_arch_dba_reviewer` |
| `ft_qa_acceptor` | 测试QA验收人 | Foundation测试治理组 | `foundation_qa_acceptor` |
| `ft_independent_reviewer` | 测试独立准入评审人 | Foundation测试治理组 | `foundation_independent_reviewer` |

用户列表与详情仅在 `userType='99'` 时显示“测试身份”标签。系统没有为该切片增加密码或 Hash 的显示、复制、下载、导出接口；界面也不提供这些能力。

## 3. 重复运行与修复边界

- 首次成功运行精确创建 6 个测试部门、12 个测试用户和 12 条唯一目标角色关系。
- 再次运行复用带完整 marker 的记录，数量保持不变，已有 BCrypt Hash 字节不变。
- Seeder 可以把已标记测试用户的部门和角色关系修复为目录中的唯一目标，但不会修改普通用户或把 Q-003 冲突角色授予任何身份。
- 已软删除的测试用户作为历史记录保留；再次显式运行会创建新的活动记录，不会恢复历史审批动作。
- 正式角色缺失、停用、删除或权限漂移，以及真实用户或部门碰撞，都会在预检或单一事务中失败并回滚。
- 当前只承诺单次启动调用；并发应用实例同时启动 Seeder 不在本切片支持范围内。

## 4. 稳定错误码

| 错误码 | 含义 |
|---|---|
| `FOUNDATION_TEST_IDENTITIES_PROFILE_FORBIDDEN` | 活动 Profile 不允许，尤其是包含 `prod` |
| `FOUNDATION_TEST_IDENTITIES_PASSWORD_REQUIRED` | 开启时没有运行时密码 |
| `FOUNDATION_TEST_IDENTITIES_PASSWORD_WEAK` | 密码长度或弱口令规则不满足 |
| `FOUNDATION_TEST_IDENTITIES_ROLE_MISSING` | 必需的正式业务或治理角色缺失 |
| `FOUNDATION_TEST_IDENTITIES_ROLE_MISMATCH` | 正式角色状态、语义或最小权限集合不匹配 |
| `FOUNDATION_TEST_IDENTITIES_USER_CONFLICT` | 预留用户名被非测试身份占用 |
| `FOUNDATION_TEST_IDENTITIES_DEPARTMENT_CONFLICT` | 预留部门代码或名称与真实部门冲突 |

## 5. Marker 与清理

清理不能只按 `ft_%`、`user_type='99'`、部门名称或单个字段匹配。测试数据的稳定识别条件是：

- 用户同时满足 `user_type='99'`、`create_by='foundation-test-seeder'`，且备注以前缀 `FOUNDATION_TEST_IDENTITY|DO_NOT_USE_FOR_PRODUCTION_EVIDENCE` 开始；
- 部门同时满足下列精确预留 `dept_code` 和 `create_by='foundation-test-seeder'`：`FOUNDATION_TEST_FIRM`、`FOUNDATION_TEST_SALES`、`FOUNDATION_TEST_CASE_MANAGEMENT`、`FOUNDATION_TEST_GENERAL_LAW`、`FOUNDATION_TEST_FINANCE`、`FOUNDATION_TEST_GOVERNANCE`。

受控清理时先关闭 Seeder 并重启，备份并核对完整 marker 集合；随后在一个事务内先删除已标记用户的岗位/角色关系和用户，再按“子部门、根部门”顺序删除精确标记部门。不得删除五个正式治理角色，也不得把测试身份产生的审批、Owner 或 Reviewer 动作转换成生产证据。自动化测试使用同一精确 marker 做确定性清理，并恢复测试前的自增值。

## 6. 验证记录（2026-07-19）

验证使用唯一 disposable `mysql:8.0` 容器、动态 loopback 端口和运行时随机凭据；服务器报告 MySQL `8.0.46`。既有 `lawfirm-mysql` 仅被列出确认，未执行任何变更命令。三个独立空 schema 均按 CI 顺序装载精确 11 个 v0.15 基线文件。

| 门禁 | 结果 |
|---|---|
| `mvn --batch-mode --no-transfer-progress clean verify` | `BUILD SUCCESS`；722 tests，0 failure，0 error，12 skip。12 个 skip 中 10 个是未注入外部数据库时的显式 MySQL gate，2 个是 Windows 不具备符号链接能力时的既有 capability assumption；数据库 gate 随后在 disposable MySQL 上以 0 skip 执行 |
| CI migration 完整列表 | 4/4 PASS，0 skip；Flyway 终态 `0.20.28`，`flyway_schema_history` 41 条（含 baseline） |
| 隔离 Foundation 列表 | 8/8 PASS，0 skip；未启用 Seeder 时测试部门、`ft_%` 用户和 `user_type='99'` 用户均为 0；启用后测试断言精确 6/12/12，第二次运行 Hash 字节不变，冲突用例全部回滚 |
| 治理角色与清理 | 5 个 V0.20.28 正式治理角色及精确最小权限通过；测试结束后 marker 用户和部门均清理为 0 |
| 前端契约 | Todo UI、Todo Schema、Foundation CI、测试身份 UI、UTF-8 全部 PASS |
| 生产构建 | PASS；仅保留既有 2 项 bundle-size warning |
| Playwright | 36/36 PASS：既有 34 个用例加 2 个测试身份用例，0 failure |

CI migration 列表的第一次 RED 暴露了共享 schema 的阶段边界：文件材料 E2E 先迁移到 V0.20.28，会使分阶段验证 V0.20.27→V0.20.28 的迁移契约失去前置状态。该 E2E 已固定在其实际依赖的 V0.20.27；从新 11 文件基线复跑后 4/4 通过，最终仍由迁移契约推进到 V0.20.28。

## 7. 准入事实边界

本手册所证明的结论仅是“正式治理角色与隔离测试身份切片验证通过”。这些身份不能自动填写真实 Owner/Reviewer，不能关闭 G-01～G-07，不能把 Mock/CI 动作计为生产签字，也不能把 Foundation 或 Todo Engine 总体状态提升为 `ADMITTED`。

仓库当前总体结论仍为 `NOT_ADMITTED`；只有真实运行环境、真实责任人和独立证据闭环后，才能按准入流程重新计算。
