# Todo Engine Phase Two Design

## Goal

在 V0.16 已验收的 Todo Engine 基础上，完成合同、案件、办理、结案和归档的标准待办链路；提供模板配置、链路追踪、异常事件和 SLA 运营前端；让业务详情页直接展示关联待办。阶段二采用“系统预置标准模板 + 管理后台可复制、修改并发布不可变版本”的混合模式。

## Delivery Baseline

- 开发分支：`V0.17`，基线为 V0.16 最终提交 `62a6394765c1a1106d39cba0bf0d75a6e9148243`。
- 继续使用模块化单体：`law-todo` 负责待办领域，`ruoyi-system` 负责业务事实，`ruoyi-admin` 暴露 API，`ruoyi-ui` 提供管理端。
- 业务 Service 只发布 Outbox 事件或由 Completion Handler 调用，不允许 Controller、前端或业务 Mapper 直接写 Todo 表。
- 不引入 MQ、微服务、BPM、复杂表达式 DSL、可视化拖拽设计器、多租户或移动端。

## Architecture

阶段二延续事件驱动的模块化单体，并增加三个明确边界：

1. `TodoDefinitionService` 负责复制模板、草稿版本、发布校验、版本对比和启停触发规则。
2. `TodoBusinessOrchestrator` 由一组业务专用 Completion Handler/Validator 组成，只调用公开业务 Service，不直接写业务 Mapper。
3. `TodoOperationsService` 负责链路查询、业务对象摘要、失败事件、SLA 升级、人工重放和受控异常处理。

业务动作与待办完成保持同一数据库事务；业务动作产生的新 Outbox 事件在事务提交后由处理器生成下一待办。这样可避免业务事实成功但待办错误完成，也避免业务回滚后产生下一节点。

## Hybrid Template Model

### Standard definitions

系统通过 Flyway 预置可直接运行的标准模板、版本和触发规则。预置内容使用稳定 `template_code`，迁移可重复执行且不会覆盖管理员已经发布的新版本。

### Editable definitions

管理员只能编辑草稿；已发布版本永久只读。复制模板生成新的稳定模板代码，复制版本生成下一草稿版本。发布时校验：

- Owner 规则能够解析；
- DoD、SLA、下一节点和 UI Schema 是合法 JSON；
- SLA 引用有效工作日历；
- 下一节点引用已发布模板版本；
- 事件类型和业务类型有效；
- 同一事件、业务类型和模板版本不存在重复启用规则。

发布使用状态条件更新，重复发布和并发发布返回稳定业务错误码。

## Standard Business Chains

### Contract chain

| Business fact | Generated todo | Owner rule | Completion action | DoD |
|---|---|---|---|---|
| `CONTRACT_SUBMITTED` | 合同审核 | `ROLE:CONTRACT_REVIEWER` | 调用合同审核 Service | 审核动作、意见；退回/拒绝必须填写原因 |
| `CONTRACT_APPROVED` | 合同签署 | 合同负责人 | 调用合同签署 Service | 签署方式、签署日期、签署文件 |
| `CONTRACT_SIGNED` | 收款确认 | `ROLE:FINANCE` | 调用收款确认 Service | 费用计划、金额、凭证 |
| `PAYMENT_CONFIRMED` | 开票处理 | `ROLE:FINANCE` | 调用开票 Service | 发票动作；开票时必须有发票号/附件 |
| `INVOICE_HANDLED` | 案件生成检查 | `ROLE:CASE_MANAGER` | 调用案件生成 Service | 合同已签、首笔收款满足、必要材料齐全 |

付款分期允许每个费用计划生成独立待办，幂等键包含费用计划 ID。未要求开票时，开票模板通过事件条件跳过并直接进入案件生成检查。

### Case-management chain

| Business fact | Generated todo | Owner rule | Completion action | DoD |
|---|---|---|---|---|
| `CASE_CREATED` | 案管分类与分案 | `ROLE:CASE_MANAGER` | 调用案件分案 Service | 案件类型、主办律师、协办成员、分案说明 |
| `CASE_ASSIGNED` | 律师接案确认 | 主办律师 | 调用接案确认 Service | 接受或拒绝；拒绝必须填写原因 |
| `CASE_REJECTED` | 重新分案 | `ROLE:CASE_MANAGER` | 调用案件分案 Service | 新主办律师和重新分案说明 |
| `CASE_TRANSFER_REQUESTED` | 转案审批 | `ROLE:CASE_MANAGER` | 调用转案审批 Service | 同意/拒绝及审批意见 |
| `CASE_TRANSFER_APPROVED` | 新律师接案确认 | 新主办律师 | 调用接案确认 Service | 接受或拒绝及说明 |

分案、接案和转案使用现有业务状态条件更新；旧负责人名下未完成的办理待办在转案批准后统一取消并记录原因，新负责人待办由事件生成。

### Matter, closing and archive chain

阶段二新增稳定业务事件：

- `MATTER_NODE_READY`
- `MATTER_NODE_COMPLETED`
- `MATTER_EXPENSE_SUBMITTED`
- `MATTER_DOCUMENT_REQUIRED`
- `ARCHIVE_APPLIED`（已有）
- `CASE_CLOSED`（已有）
- `CASE_ARCHIVED`（已有）

标准链路：

| Business fact | Generated todo | Completion action | DoD |
|---|---|---|---|
| `MATTER_NODE_READY` | 办理案件节点 | 更新节点完成事实 | 动态字段、实际日期、节点材料 |
| `MATTER_EXPENSE_SUBMITTED` | 费用审核 | 更新付款/报销/凭证状态 | 审核动作、意见、凭证 |
| `MATTER_DOCUMENT_REQUIRED` | 补充案件文档 | 新增案件文档 | 文档类型、文件、说明 |
| `ARCHIVE_APPLIED` | 结案确认 | 调用确认结案 Service | 节点完成、费用清理、结案意见 |
| `CASE_CLOSED` | 归档确认 | 调用确认归档 Service | 归档材料完整、归档编号 |

节点完成后由节点配置决定下一节点；没有下一节点时生成“申请结案”候选待办。归档完成后关闭该案件全部非终态待办并保留审计。

## Cross-cutting Todo Behavior

### Business references and chain view

一个待办可以关联合同、案件、费用计划、案件节点、文档和归档记录。业务详情摘要按 `business_type + business_id` 查询：进行中数量、超时数量、下一截止时间、当前负责人和最近动作。链路视图按 `root_todo_id` 返回有序节点、状态、Owner、SLA、动作和异常。

### Exception operations

阶段二新增受控异常动作：

- 强制完成：仅 `todo:exception:force-complete`，必须填写原因并满足不可绕过的业务校验；
- 强制取消：仅非终态，必须填写原因；
- 重新生成：仅针对 DEAD 事件或已取消且没有活动幂等实例的待办；
- 重新投递：复用 Outbox DEAD 重放；
- 批量转派：仅同一业务范围内非终态待办，逐条状态条件更新；
- SLA 豁免：记录原截止时间、新截止时间、原因和审批人，不删除既有阈值记录。

每个异常动作写入独立不可变日志，不允许直接修改历史动作日志。

### Notifications

继续使用站内定向通知。阶段二增加：待领取、退回、转派、强制处理、事件失败、批量转派和 SLA 豁免通知。通知以 `todo_id + user_id + notification_type + source_id` 幂等。

## Permissions

新增权限：

- `todo:template:list|add|edit|copy|publish`
- `todo:trigger:list|edit`
- `todo:calendar:list|edit`
- `todo:chain:query`
- `todo:operations:list`
- `todo:event:replay`
- `todo:exception:force-complete|cancel|regenerate|transfer|sla-waive`

功能权限不能替代数据权限。异常操作还必须通过 `TodoAccessPolicy` 验证管理员的数据范围；抄送人保持只读。

## Backend APIs

### Definition management

- `GET/POST/PUT /todo/template`
- `POST /todo/template/{id}/copy`
- `GET /todo/template/{id}/versions`
- `POST /todo/template/{id}/version/{version}/copy`
- `POST /todo/template/{id}/publish`
- `GET/POST/PUT /todo/template/trigger`
- `GET/POST/PUT /todo/calendar`

### Business views

- `GET /todo/business/{type}/{id}/summary`
- `GET /todo/business/{type}/{id}/list`
- `GET /todo/chain/{rootTodoId}`

### Operations

- `GET /todo/operations/dashboard`
- `GET /todo/operations/events`
- `POST /todo/operations/events/{id}/replay`
- `POST /todo/operations/{id}/force-complete|force-cancel|regenerate|sla-waive`
- `POST /todo/operations/batch-transfer`

所有写接口使用强类型 DTO，要求 `actionId` 和原因；响应包含稳定 `businessCode`。

## Frontend

### Todo configuration center

新增配置中心，包含模板列表、版本抽屉、复制模板、编辑草稿、发布确认、触发规则和工作日历。JSON 配置使用结构化表单编辑 Owner、DoD、SLA 和下一节点；高级 JSON 只作为只读预览，避免用户手写无效配置。

### Operations center

新增运营工作台：积压指标、超时分布、150% 升级、失败/DEAD 事件、人工重放、异常处理和批量转派。危险操作使用二次确认并要求原因。

### Business embedding

合同、案件和事项详情页嵌入统一 `BusinessTodoSummary`；点击后打开 `BusinessTodoDrawer`，展示活动待办及完整链路。组件只调用 Todo API，不复制业务页面状态逻辑。

### Existing Todo center

增加“全部可见”“部门/下属”“抄送我”视图、链路入口、异常标识和来源业务跳转；保留本人、候选池和超时视图。

## Error Handling and Consistency

- 所有 Completion Handler 与业务动作处于同一事务；异常则待办状态不变。
- Outbox 创建下一待办失败沿用失败退避、DEAD 和自动回收。
- 模板发布、批量转派、强制动作和 SLA 豁免均使用状态条件更新与 `actionId` 幂等。
- 业务对象不存在、状态变化、Owner 变化、材料不足、重复处理、越权和并发冲突返回不同业务错误码。
- 前端仅在成功响应后刷新业务详情和待办摘要；失败保留用户输入。

## Database Changes

新增或扩展：

- `todo_template_version.status`：`DRAFT/PUBLISHED`；
- `todo_template_version.source_version_id`：复制来源；
- `todo_exception_log`：强制动作、批量转派、重新生成和 SLA 豁免；
- `todo_sla_waiver`：原截止时间、新截止时间、原因、审批人；
- `todo_notification.source_id`：通知幂等来源；
- 链路、业务摘要、事件运营和异常查询索引；
- 阶段二标准模板、版本、触发规则、菜单和权限迁移。

已执行 Flyway 文件永不修改；阶段二从 `V0_17_1` 开始新增迁移。

## Testing

### Unit and service tests

- 模板复制、草稿编辑、发布校验、并发发布；
- 每个 Completion Handler 的成功、业务前置条件失败和事务回滚；
- 合同、案件、节点、费用、结案和归档 DoD；
- 异常动作权限、幂等、并发、不可绕过校验；
- 工作日历、SLA 豁免、阈值和主管升级；
- 业务摘要和链路排序。

### MySQL integration tests

- 从 v0.15 基线执行 Flyway 到阶段二最终版本；
- 预置模板及触发规则完整性；
- 模板版本不可变约束；
- 事件、动作、异常动作和通知幂等键；
- 状态条件更新、批量转派和 SLA 豁免事务。

### API and frontend tests

- MockMvc 覆盖所有新增 API 的校验、权限和业务错误码；
- 前端契约检查覆盖配置中心、运营中心和三个业务内嵌组件；
- 生产构建通过；
- Playwright E2E 覆盖合同提交到案件生成、案件分案到接案、节点办理到归档三条主链路。

## Acceptance Criteria

阶段二只有在以下全部成立时完成：

1. 标准模板开箱可运行，管理员可复制、编辑草稿和发布新版本，历史实例不受影响；
2. 合同提交至案件生成的全部标准待办可端到端运行；
3. 案件生成、分案、接案、拒接、转案可端到端运行；
4. 节点办理、费用/文档、结案和归档可端到端运行；
5. 合同、案件和事项详情能展示正确的待办摘要与链路；
6. 运营后台能查看失败事件、重放、强制处理、批量转派和 SLA 豁免；
7. 重复事件、重复动作和并发操作不产生重复事实；
8. 越权、材料不足和非法状态变化均被拒绝并记录；
9. Maven 全量构建、前端生产构建、MySQL/Flyway、API、集成和 E2E 测试全部成功；
10. 远程 V0.17 CI 所有质量门禁绿色，并形成阶段二验收报告。
