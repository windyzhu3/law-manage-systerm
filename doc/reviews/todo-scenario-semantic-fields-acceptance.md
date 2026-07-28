# Todo Engine 场景工作台与语义字段验收报告

验收日期：2026-07-28

实现提交：`aa99490a feat: add governed todo scenario workbench`

验收环境：Chrome、Vue 生产构建、RuoYi 后端、MySQL 8、Redis

## 验收结论

本批功能通过验收。首联待办 `TD-001` 已具备面向业务用户的场景化模拟工作台，三个必测场景全部通过后才允许重新执行发布预检。人员、部门、岗位、角色、字典和业务对象字段统一通过语义目录显示名称，配置端不再把内部 ID 作为用户文案。

## 浏览器验收

使用真实后端和真实 MySQL 数据选择线索对象后，依次验证：

| 场景 | 预期下一待办 | 实际下一待办 | 结果 |
|---|---|---|---|
| 有效首联 `TD001_VALID` | `TD-004` | `TD-004` | 通过 |
| 疑似无效 `TD001_SUSPECT_INVALID` | `TD-002` | `TD-002` | 通过 |
| 未接通 `TD001_UNREACHABLE` | `TD-003` | `TD-003` | 通过 |

批量执行完成后：

- 发布门禁状态为“通过”，阻断场景为 0；
- 重新执行发布预检后，阻断项为 0、警告项为 0；
- 浏览器控制台在最终验收轮次无错误；
- 人员和部门字段显示名称，选择器使用受治理的语义选项；
- 高级覆盖项独立折叠，日常操作不要求用户填写 JSON 或内部编码。

页面证据：`output/playwright/todo-scenario-semantic-fields-acceptance.png`

## 数据库无副作用证明

批量模拟前后，对以下九张运行期和线索业务表执行全列、全行 SHA-256 指纹比对，行数和内容均未变化：

- `todo_instance`
- `todo_action_log`
- `business_event`
- `biz_lead`
- `biz_lead_followup`
- `biz_lead_assignment_log`
- `biz_lead_call_record`
- `biz_lead_invalid_review`
- `todo_schedule_plan`

模拟仅新增三条 `todo_simulation_evidence` 证据。证据中只保存场景代码、预期/实际目标、轨迹代码和输入哈希；针对测试客户名称及业务编号的敏感值扫描结果为 0。

对应自动化证明：`TodoScenarioSimulationExternalMysqlIT`。

## 自动化验证

- 后端：`mvn clean test` 通过；
- 真实 MySQL：`TodoScenarioSimulationExternalMysqlIT` 通过；
- 前端 Todo、配置中心、阶段二、Schema、基础治理、身份、E2E 服务、E2E 契约、编码检查全部通过；
- MySQL E2E 数据库安全检查通过；
- Vue 生产构建通过。

生产构建仍有四类既有非阻断告警：两个 CSS 顺序告警、资源体积告警和入口体积告警。本批未新增编译告警。

## 已知环境数据说明

验收库中的一个历史部门名称本身存在旧编码污染。语义解析已正确返回数据库中的部门名称而非部门 ID；该历史数据清洗不属于本批 Todo 场景工作台实现，应在数据治理任务中单独修复。
