# Todo Engine Phase One Design

## Goal

在现有 RuoYi 模块化单体中新增独立 `law-todo` 模块，完整提供可复用的待办模板、实例、分派、DoD、SLA、审计、事件触发和下一待办能力，并以“线索分配 → 首联”作为第一条端到端业务链路。

## Architecture

`law-business` 继续承载跨模块契约；`law-todo` 承载待办领域、应用服务和持久化；`ruoyi-admin` 暴露 API；`ruoyi-quartz` 触发 SLA 扫描；现有业务模块只发布 Outbox 事件或调用明确的业务 Service，不直接写待办表。Todo Engine 不直接修改线索、合同或案件状态，完成待办时通过注册的完成处理器调用现有业务能力。

## Scope

阶段一包含：

- 模板、模板版本和触发规则；
- 待办实例、业务关联、候选人、抄送人和附件；
- 创建、领取、开始、提交、完成、退回、转派和取消；
- Owner、候选用户、角色、部门和岗位分派；
- DoD 必填字段、必传附件和业务校验处理器；
- SLA 工作日历、计时、暂停、恢复、80% 提醒、100% 超时、150% 升级；
- Outbox 幂等消费、失败重试与人工重放兼容；
- 上一待办、根待办、下一待办和链路追踪；
- 本人、候选池、部门和下属的数据访问策略；
- 待办中心、工作台指标、详情抽屉和业务对象内嵌摘要；
- 线索分配完成后生成首联待办，首联完成后记录线索跟进事实。

阶段一不包含可视化流程设计器、复杂表达式 DSL、MQ、微服务、多租户、移动端和全业务链路接入。

## Domain Model

- `TodoTemplate`：稳定模板标识、名称、业务类型、状态和当前版本。
- `TodoTemplateVersion`：不可变版本，保存 Owner 规则、DoD、SLA、下一节点和页面配置快照。
- `TodoTriggerRule`：将业务事件类型映射到模板版本并生成幂等键。
- `TodoInstance`：保存责任人、状态、优先级、截止时间、SLA 状态和链路标识。
- `TodoCandidate`：候选用户、角色、部门或岗位。
- `TodoCc`：抄送和关注人。
- `TodoRelation`：关联线索、客户、合同、案件、节点和文档等业务对象。
- `TodoActionLog`：每次状态动作的不可变审计记录。
- `TodoAttachment`：DoD 材料及其类型。
- `TodoSlaRecord`：计时起止、暂停累计、阈值触发和升级记录。
- `TodoWorkCalendar`：工作日、工作时段和例外日期。

## State Machine

`CREATED → CLAIMED → IN_PROGRESS → SUBMITTED → COMPLETED` 为主路径；候选池可从 `CREATED` 领取；`SUBMITTED → RETURNED → IN_PROGRESS` 支持退回；未完成状态可转派；业务撤销可进入 `CANCELLED`。所有动作使用状态条件更新和版本号，重复请求通过 `actionId` 唯一键返回原结果。

## Event and Idempotency

Todo 事件处理器实现现有 `BusinessEventHandler`。触发规则使用 `event_id + template_version_id + business_id` 生成唯一键；待办创建、下一待办生成和动作提交均有数据库唯一约束。消费失败沿用现有 Outbox 重试与 DEAD 管理机制。

## Permissions

功能权限使用 `todo:list/query/claim/start/submit/complete/return/transfer/cancel/template/manage`。数据访问由 `TodoAccessPolicy` 统一判断：Owner 可操作；候选人可领取；部门主管可查看下属；模板管理员可管理配置；抄送人仅只读。

## API

- `GET /todo/dashboard`
- `GET /todo/list`
- `GET /todo/{id}`
- `POST /todo/{id}/claim|start|submit|complete|return|transfer|cancel`
- `GET/POST/PUT /todo/template`
- `POST /todo/template/{id}/publish`
- `GET/POST/PUT /todo/calendar`
- `POST /todo/sla/scan`

写接口接收强类型 DTO，并要求 `actionId`；响应沿用 `AjaxResult`，业务失败返回现有 `BusinessErrorCode`。

## Frontend

新增 `/todo/index.vue` 页面，包含指标卡、查询栏、本人/候选池/超时视图、表格和详情抽屉。动作表单复用统一弹窗组件，附件复用 `FileUpload`，状态和优先级使用字典。工作台通过独立 `TodoSummaryCard` 组件展示待办数量，业务详情通过 `TodoRelationPanel` 展示关联待办。

## Testing and Gates

- 每个状态动作必须先有失败测试，再实现生产代码；
- 覆盖合法/非法转换、幂等、并发、权限、DoD、SLA 阈值、暂停恢复、事件重复消费和下一待办；
- Mapper 使用 MySQL 集成测试或迁移初始化测试验证唯一键和条件更新；
- 首联链路必须具备 Service 集成测试和 API 测试；
- `mvn -DskipTests=false package`、前端生产构建、Flyway 从零迁移和 GitHub Actions 全部通过。

## Acceptance Criteria

阶段一完成必须同时证明：所有上述 API 可用；所有动作有审计；重复请求不产生重复待办或动作；SLA 三阈值只触发一次；越权访问被拒绝；线索分配可可靠生成首联待办；首联完成可写入跟进记录；全量自动化测试及 CI 成功。
