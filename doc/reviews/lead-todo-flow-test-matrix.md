# 线索待办流真实 MySQL 测试矩阵

## 1. 验收边界

- 数据库：MySQL 8.0 / InnoDB，使用 11 个 `sql/` v0.15 基线脚本初始化，并由 Flyway 前向迁移至当前版本。
- 运行端口：真实 MyBatis Mapper、`LeadAssignmentService`、`BusinessEventOutboxProcessor`、`TodoEventService`、`TodoCommandService`、业务 Completion Handler、`TodoScheduleService`、`TodoSlaService`。
- 模板：运行时从数据库发现最新已发布 TD-001、TD-002、TD-003、TD-004 数字版本，不接受仓库内固定 ID。
- 隔离：每个场景使用唯一部门、用户、线索、证据与策略；业务、Todo、Outbox 和审计数据在场景结束时一并回滚。
- 禁止：H2、Mock 数据库、跳过测试、固定模板 ID、只验证页面 Mock API。

## 2. 场景矩阵

| # | 场景 | Fixture | 动作 | 业务断言 | Todo / 调度断言 | 审计 / 幂等断言 | 精确测试方法 |
|---:|---|---|---|---|---|---|---|
| 1 | 分配 Outbox → TD-001 → VALID → TD-004 | 独立部门、销售、主管、VALID 线索、受控文件证据 | `LeadAssignmentService.assign` 同事务写分配事实与 Outbox；真实 processor 消费；经 claim/start/submit/complete 完成 TD-001 | 首联结果为 `VALID`；通话事实关联来源 Todo | TD-001 Owner/部门/SLA 正确；只生成一个 TD-004 | 分配事件 `PROCESSED`；TD-001 只有一个 COMPLETE；再次消费不新增 Todo | `LeadTodoFlowEndToEndTest#assignedOutboxCreatesOwnedTd001AndValidCompletionCreatesOneTd004` |
| 2 | SUSPECT_INVALID → TRUE_INVALID → Dead-Pool | 独立 SUSPECT 线索、销售证据、主管 | 完成 TD-001 后由主管完成 TD-002 | `disposition=DEAD_POOL`；存在 ENTER 日志 | TD-002 完成且没有错误后继 Todo | Dead-Pool 日志绑定原始 TD-001；TD-002 COMPLETE 审计唯一 | `LeadTodoFlowEndToEndTest#suspectInvalidManualConfirmationMovesLeadToDeadPoolWithAudit` |
| 3 | SUSPECT_INVALID → MISJUDGED_VALID → 重开 TD-001 | 独立 SUSPECT 线索、销售、主管 | 完成 TD-001；主管判定误判有效 | 首联重开；生成质量记录并保留销售 Owner | TD-002 只生成一个重开 TD-001；root/route token 连续 | 质量、分支事件、next idempotency key 唯一 | `LeadTodoFlowEndToEndTest#migratedLeadGraphExecutesThroughProductionPortsWithoutOrphansOrDuplicates` |
| 4 | TD-002 到期 → COMPLETE_DEFAULT → Dead-Pool | TD-002 `CREATED` 实例、24 小时默认规则、主管 | 将 DUE/SLA 绝对时间置为已到期；运行真实 `TodoAutoActionService.scanDue` | 复核记录 `system_default=Y`；线索进入 Dead-Pool | 系统在同事务按 CREATED→CLAIMED→IN_PROGRESS→SUBMITTED→COMPLETED 推进 | 自动执行、自动审计、系统状态动作各唯一；完整 DoD/handler 未绕过 | `LeadTodoFlowEndToEndTest#overdueTd002RunsCompleteDefaultAndMovesLeadToDeadPool` |
| 5 | UNREACHABLE → T0 → T1_AM → CONNECTED | 独立 UNREACHABLE 线索、七窗口策略、文件证据 | T0 三次未接通；物化 T1_AM；完成 CONNECTED | 首联结果回写 `VALID` 与四项客户信息 | 生成一个 TD-004；计划 `CONTACTED`；剩余五窗口取消 | 每个 occurrence/Todo/动作唯一，当前及未来均无活动重试 | `LeadTodoScheduleEndToEndTest#unreachableAdvancesToT1AndConnectedCancelsFutureWindowsAndCreatesTd004` |
| 6 | T0/T+1/T+2 全窗口耗尽 → 公海 | 独立 UNREACHABLE 线索、七个绝对时间窗 | 按窗口顺序物化；T0 三次、其余各一次未接通 | `disposition=PUBLIC_POOL`、`pool_status=1`、`retry_stage=EXHAUSTED` | 计划 `EXHAUSTED`；无活动 TD-003 | 只发布一个 `LEAD_RETRY_EXHAUSTED`；窗口/Todo 无重复 | `LeadTodoScheduleEndToEndTest#allT0T1T2WindowsExhaustedReturnsLeadToPublicPoolExactlyOnce` |
| 7 | 请假跳过、委托、轮转 | 销售标记 UNAVAILABLE、有效委托给主管、同部门第三候选 | 生产组织适配器解析 BUSINESS_OWNER；连续两次 ROUND_ROBIN | Owner 委托给可用主管；不可用原 Owner 不入轮转池 | 两次轮转覆盖两个可用候选 | 持久化一个策略游标，原子推进且候选集合受控 | `LeadTodoScheduleEndToEndTest#leaveUsesDelegateAndRoundRobinSkipsUnavailableOwner` |
| 8 | SLA 80/100/150 | 有 Owner/主管的活动 TD-001、三个已到期阈值 | 真实 `TodoSlaService.scanAndEscalate` 扫描两次 | 主管获得 150% 升级可见通知 | 最终 `sla_status=ESCALATED` | Owner 收 80/100/150 三条，主管收一条 150；二次扫描为 0 | `LeadTodoScheduleEndToEndTest#sla80_100_150FireOnceAndSupervisorReceivesEscalation` |
| 9 | 重复事件、完成动作、路由、窗口幂等 | 稳定事件键、完成 actionId/payload、occurrence key | 重复插入/消费 LEAD_ASSIGNED；对同一 TD-001 以相同 actionId、payload、actor 调用两次真实 `complete`；再用同 actionId 修改 payload；重复扫描 T1_AM | 相同请求重放返回同一完成结果，followup/call fact/分支事件均唯一；冲突重放返回 `TODO_ACTION_ID_CONFLICT` | COMPLETE 审计、route token/join/relation、TD-004 与窗口实例计数在重放后不变 | 事件唯一键、完成 action 内容指纹、next idempotency key 与 occurrence key 分别受控，不用 Outbox 唯一键代替完成幂等证明 | `LeadTodoFlowEndToEndTest#duplicateEventAndCompletionActionReplayAreIdempotentAndConflictIsRejected`；`LeadTodoFlowEndToEndTest#migratedLeadGraphExecutesThroughProductionPortsWithoutOrphansOrDuplicates` |
| 10 | 真实生产完成链中途失败整体回滚并可恢复 | 已提交为 SUBMITTED 的 TD-001；Spring `DataSourceTransactionManager` 嵌套事务；可替换 completion lifecycle port | 第一次在真实 handler 已写 followup/call fact/Outbox 后抛错；第二次在真实状态动作、routing、TD-004/relation 已写后抛错；均让异常逃逸并由 Spring 自动回滚；最后用相同 action 正常重试 | 每次失败后 Lead 状态/row version、followup、call fact、分支 Outbox 全部恢复；正常重试写入一次 | 每次失败后 Todo 状态/version、COMPLETE action、route token/join、relation、TD-004 全部恢复；正常重试完成并生成一个 TD-004 | 故障端口在抛错前直接查询同一 InnoDB 事务，证明前置生产写入已发生；测试不手写业务 SQL、不直接调用 rollback/savepoint | `LeadTodoFlowEndToEndTest#productionCompletionTransactionRollsBackHandlerAndRoutingFailuresThenRecovers` |

## 3. 事务与一致性矩阵

| 边界 | 成功条件 | 失败条件 | 证据 |
|---|---|---|---|
| 分配事务 | `biz_lead`、assignment log、`business_event` 同时可见 | 任一步失败全部回滚 | 场景 1；`LeadFlowMapperExternalMysqlIT#factLeadTransitionAndOutboxShareOneRealMysqlTransaction` |
| Outbox 消费 | 事件标记 `PROCESSED` 且只创建一个有 Owner 的 TD-001 | Schema/Owner/处理失败进入受控重试，不产生孤儿 Todo | 场景 1、9 |
| Todo 完成 | 业务事实、动作日志、路由 token、下一 Todo 同事务 | handler/Outbox 后或 routing/next-Todo 后故障均由真实 Spring/MySQL 事务整体回滚，相同 action 可恢复成功 | 场景 2、3、10 |
| 自动完成 | execution claim、系统状态动作、DoD、handler、最终审计一致 | 状态/DoD/handler 失败保留可重试执行且不伪装成功 | 场景 4 |
| 时间窗 | occurrence、TD-003、窗口状态、计划状态一致 | 重复物化/完成不产生第二实例 | 场景 5、6、9 |
| SLA | 每个阈值只推进一个 optimistic version | 多表 affected-row 计数不得跳版本 | 场景 8；`TodoSlaServiceTest#scanMarksReachedThresholdsOnce` |

## 4. 精确执行命令

```powershell
$env:TODO_MIGRATION_DB_URL='jdbc:mysql://127.0.0.1:3306/law_lead_todo_e2e?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&connectionCollation=utf8mb4_unicode_ci'
$env:TODO_MIGRATION_DB_USER='root'
$env:TODO_MIGRATION_DB_PASSWORD='root'

mvn -pl ruoyi-admin -am "-Dtest=FlywayMigrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -pl ruoyi-admin -am "-Dtest=LeadTodoFlowEndToEndTest,LeadTodoScheduleEndToEndTest" "-Dsurefire.failIfNoSpecifiedTests=false" test

Remove-Item Env:TODO_MIGRATION_DB_URL
Remove-Item Env:TODO_MIGRATION_DB_USER
Remove-Item Env:TODO_MIGRATION_DB_PASSWORD
mvn test
```

验收时三条 Maven 命令必须全部为 `BUILD SUCCESS`；专用真实库命令中的两个 Task 11 类不得有 skipped test。普通全仓回归未提供外部数据库时，外部库测试遵循仓库既有约定跳过。
