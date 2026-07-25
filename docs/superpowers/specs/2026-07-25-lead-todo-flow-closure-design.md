# 线索待办流程闭环设计

## 1. 背景与目标

本设计在现有 RuoYi、`law-business`、`law-todo`、Outbox、文件中心和 Todo 配置中心基础上，完整实现律所流程图中阶段1-2的线索待办流程：

```text
线索进入
→ 标签确认
→ 轮转分配
→ TD-001 首联
   ├─ 有效 → TD-004 5天实质进展
   ├─ 疑似无效 → TD-002 主管复核
   │              ├─ 确认无效 → Dead-Pool
   │              └─ 误判有效 → 重开 TD-001
   └─ 无法联系 → TD-003 T0/T+1/T+2 重试
                  ├─ 联系成功 → TD-004
                  └─ 次数耗尽 → 公海
```

目标不是在线索 Service 中复制一套流程代码，而是同时修复当前测试和审计发现的 Todo Engine 通用缺口，使后续合同、案管、案件等模板可以直接复用事件契约校验、组织解析、时段调度、字典动态表单、自动完成和正式路由。

## 2. 已确认的产品边界

1. 外呼一期实现“人工补录通话记录 + 上传录音/截图凭证 + 标准外呼适配器接口”，不绑定具体供应商。
2. T0 默认从首次首联开始后 2 小时内最多拨打 3 次。
3. T+1、T+2 默认各设置上午、午间、下午三个联系窗口，每个窗口生成一个可追踪的待办实例。
4. 重试规则允许按渠道、业务类型和销售组覆盖；未命中覆盖规则时使用默认规则。
5. 无效原因固定为：无需求、否认提交、竞品干扰、其他。
6. Dead-Pool 是独立业务处置状态，不等同于公海或逻辑删除回收站。
7. Todo Engine 负责“谁、何时、按什么条件处理以及下一待办”；线索业务模块负责业务事实和状态。
8. 外呼供应商接入、呼叫中心坐席控制和移动 APP 原生拨号不纳入本轮，但适配器契约和回调幂等必须完成。

## 3. 当前问题与根因

### 3.1 事件契约漂移

`LEAD_ASSIGNED` 生产者发送 `toOwnerId`，已发布模板及事件目录要求 `ownerId`、`ownerDeptId`、`assignmentId`。Todo 事件入口仅取得目录 Schema，却没有对事件载荷执行完整 Schema 校验，导致缺失 Owner 的事件可能生成无人可处理的待办。

### 3.2 模板目录不等于可运行模板

TD-001～TD-004 已存在定义目录，但处于 `BLOCKED`。符号化 `routingPlan` 不被运行时执行；当前 TD-001 草稿还错误引用合同签署模板版本。旧 `LEAD_FIRST_CONTACT` 已发布，但只包含简化字段和空路由。

### 3.3 组织解析器只有接口能力

通用 Owner Resolver 支持 `SUPERVISOR`、`ROUND_ROBIN`、委托和可用性语义，但数据库适配器返回空结果，且可用性只判断账户启用状态。轮转、主管、请假和委托在生产路径中不可用。

### 3.4 SLA 只覆盖持续时长

现有 SLA 可以计算分钟时长和 80/100/150 阈值，但不能表达 T0/T+1/T+2 的离散时间窗口和重复实例。TD-003 的 `schedule` 数组不会产生截止时间。

### 3.5 完成处理器和业务事实脱节

旧首联处理器只新增普通跟进记录，不回写完整首联事实，不发布分支事件，不支持 TD-001。TD-002、TD-003、TD-004 没有生产处理器。

### 3.6 动态表单缺少字典绑定

前端 `dict` 字段只有内联 `options` 才显示下拉，否则退化为文本框。模板中的首联结果、复核结果、重试阶段缺少受控字典，用户可以提交无效值。

## 4. 总体架构

```text
线索页面 / 待办中心
        │
        ▼
Todo 动态表单 ──────── 文件中心
        │
        ▼
TodoCommandService
        │ 完成校验、动作审计、幂等
        ▼
Lead Todo Completion Handler
        │ 同一事务写业务事实
        ├── biz_lead / call / review / retry / quality
        └── business_event Outbox
                    │
                    ▼
            TodoBusinessEventAdapter
                    │ Schema 校验
                    ▼
             TodoEventService
        ┌───────────┴───────────┐
        ▼                       ▼
正式路由图                时间窗口调度器
        │                       │
        └───────────┬───────────┘
                    ▼
             下一待办实例
```

所有业务写入、完成处理器和 Outbox 事件在同一数据库事务中提交。下一待办由事务提交后的 Outbox 消费产生。重复业务动作、重复事件、重复路由和重复窗口分别使用稳定幂等键阻断。

## 5. Todo Engine 通用能力设计

### 5.1 事件契约校验

新增 `TodoEventPayloadValidator`：

- 根据 `eventType + payloadVersion` 读取活动 Schema；
- 在匹配触发器和解析 Owner 前校验 required、类型、枚举和时间格式；
- 失败时抛出稳定错误码 `TODO_EVENT_PAYLOAD_INVALID`；
- Outbox 记录进入失败重试，不产生 Todo 实例；
- 错误信息包含事件类型和缺失字段，不记录手机号、录音地址等敏感值。

`LEAD_ASSIGNED` v1 固定载荷：

```json
{
  "schemaVersion": 1,
  "assignmentId": 1001,
  "ownerId": 11,
  "ownerDeptId": 103,
  "operatorId": 1
}
```

### 5.2 生产级组织适配器

扩展 `MapperTodoOrganizationAdapter`：

- `businessOwner`：按业务对象查询当前负责人；
- `supervisor`：按用户部门向上查找有效负责人，支持层级；
- `roundRobin`：使用 `todo_round_robin_cursor` 原子更新游标；
- `isAvailable`：检查账户、任职状态和 `sys_user_availability`；
- `delegateFor`：读取生效中的 `sys_user_delegation`；
- 轮转候选按稳定用户 ID 排序，游标只在成功选中后推进；
- 无可用候选时使用模板 fallback；仍为空则拒绝创建并返回 `TODO_OWNER_UNRESOLVED`，不再生成孤立待办。

### 5.3 正式路由图

TD-001～TD-004 发布前全部转换为运行时图，TASK 节点绑定已发布的数字 `templateVersionId`：

- TD-001：`VALID`、`SUSPECT_INVALID`、`UNREACHABLE` 三个条件边；
- TD-002：`TRUE_INVALID`、`MISJUDGED_VALID`，系统默认通过使用 `TRUE_INVALID`；
- TD-003：`CONNECTED`、`NEXT_WINDOW`、`EXHAUSTED`；
- TD-004：作为阶段3边界，只验证由有效分支正确生成，不实现后续 TD-005。

发布前校验目标版本必须为 `PUBLISHED`、业务类型匹配且不可引用自身草稿或无关模板。

### 5.4 时间窗口调度

新增可复用的窗口计划模型：

```text
todo_schedule_plan
todo_schedule_window
todo_schedule_occurrence
```

- 计划保存模板、业务对象、时区、规则版本、当前窗口和状态；
- 窗口保存相对天数、开始时间、结束时间、最大尝试次数；
- occurrence 唯一键：`plan_id + window_code + occurrence_no`；
- Quartz 扫描已到时间且未生成的窗口，通过 Todo Engine 创建 TD-003；
- 联系成功、确认无效、回公海或线索转化时取消剩余窗口；
- 同一窗口重复扫描不得生成第二张待办。

T0 内的三次拨号不生成三张待办，由同一 TD-003 记录三条 `biz_lead_call_record`；T+1/T+2 的每个时段各生成一张待办。

### 5.5 自动动作

`COMPLETE_DEFAULT` 允许模板声明系统完成字段：

```json
{
  "fields": {
    "reviewResult": "TRUE_INVALID",
    "reviewOpinion": "超过24小时未复核，系统按规则默认确认无效"
  }
}
```

自动动作仍执行完整 DoD、字典校验、业务处理器和路由，不提供绕过通道。

TD-001 不再在 80/100/150 全部配置 `ESCALATE`。使用现有 SLA 基线语义：

- 80%：提醒 Owner；
- 100%：标记超时并在主管视图可见；
- 150%：升级通知主管。

### 5.6 动态表单字典

UI字段增加：

```json
{
  "key": "contactResult",
  "type": "dict",
  "dictType": "law_first_contact_result"
}
```

表单查询接口解析启用字典项并返回 `options`。前端不再将空字典退化为自由文本；字典缺失时显示配置错误并禁止提交。后端 DoD 再次校验字典值。

## 6. 线索业务模型

### 6.1 `biz_lead` 扩展

新增：

```text
tag_confirm_status       标签确认状态
tag_confirm_time         标签确认时间
tag_confirm_by           标签确认人
first_contact_status     首联状态
first_contact_time       首联完成时间
first_contact_result     首联结果
city                     所在城市
visited                  是否到所
invalid_reason_code      无效原因
invalid_source_node      产生无效判断的节点
invalid_review_status    无效复核状态
retry_stage              当前重试阶段
retry_attempt_count      当前阶段尝试次数
next_retry_time          下次重试时间
disposition              ACTIVE/PUBLIC_POOL/DEAD_POOL/CONVERTED
dead_pool_time           进入Dead-Pool时间
dead_pool_reason         Dead-Pool原因
row_version              乐观锁版本
```

`status` 继续表达主生命周期；`disposition` 表达资源处置位置，避免复用逻辑删除。

### 6.2 新增业务表

- `biz_lead_call_record`：通话渠道、外部呼叫ID、开始/结束、时长、结果、录音文件对象、人工说明、接口摘要哈希、幂等键。
- `biz_lead_invalid_review`：提交原因、销售说明、复核人、复核结果、意见、系统默认标记和待办ID。
- `biz_lead_retry_record`：计划、窗口、尝试次数、联系结果、下一窗口、待办ID。
- `biz_lead_quality_record`：误判销售、原因、复核人、来源待办、质量类型。
- `biz_lead_dead_pool_log`：进入和恢复记录，不允许普通公海领取接口读取。
- `biz_business_tag`、`biz_business_tag_rel`：统一标签定义和业务对象关系。
- `todo_round_robin_cursor`、`sys_user_availability`、`sys_user_delegation`：Todo Engine 组织基础数据。

所有新表通过新的前向 Flyway 迁移创建，不修改已执行迁移。

### 6.3 字典

```text
law_first_contact_result:
  VALID / SUSPECT_INVALID / UNREACHABLE

law_lead_tag_confirm_status:
  PENDING / CONFIRMED / CORRECTED

law_lead_invalid_reason:
  NO_DEMAND / DENY_SUBMISSION / COMPETITOR_INTERFERENCE / OTHER

law_lead_invalid_review_result:
  TRUE_INVALID / MISJUDGED_VALID

law_retry_stage:
  T0 / T1_AM / T1_NOON / T1_PM / T2_AM / T2_NOON / T2_PM / EXHAUSTED

law_retry_result:
  CONNECTED / NEXT_WINDOW / EXHAUSTED

law_call_channel:
  MANUAL / APP / OUTBOUND_SYSTEM

law_lead_disposition:
  ACTIVE / PUBLIC_POOL / DEAD_POOL / CONVERTED
```

## 7. 业务服务与事件

### 7.1 服务边界

新增独立服务：

```text
LeadTagConfirmationService
LeadAssignmentPolicyService
LeadFirstContactService
LeadInvalidReviewService
LeadRetryService
LeadDeadPoolService
LeadCallRecordService
LeadQualityService
```

Todo Completion Handler 只负责将 Todo Payload 转换为命令并调用上述服务，不直接调用 Mapper。

### 7.2 事件

新增：

```text
LEAD_TAG_CONFIRMED
LEAD_FIRST_CONTACT_VALID
LEAD_SUSPECT_INVALID_MARKED
LEAD_FIRST_CONTACT_UNREACHABLE
LEAD_INVALID_REVIEW_CONFIRMED
LEAD_INVALID_REVIEW_MISJUDGED
LEAD_RETRY_WINDOW_DUE
LEAD_RETRY_CONNECTED
LEAD_RETRY_EXHAUSTED
LEAD_MOVED_TO_DEAD_POOL
```

稳定幂等键以业务事实ID构成，不使用随机值。例如：

```text
LEAD_FIRST_CONTACT_VALID:{leadId}:{followupId}
LEAD_SUSPECT_INVALID_MARKED:{leadId}:{reviewId}
LEAD_RETRY_WINDOW_DUE:{planId}:{windowCode}:{occurrenceNo}
LEAD_RETRY_EXHAUSTED:{planId}
```

### 7.3 完成处理器

- `TD-001_COMPLETE`：保存联系记录和证据，回写首联字段，按结果发布一个分支事件。
- `TD-002_COMPLETE`：保存复核；确认无效进入Dead-Pool，误判则写质量记录并发布重开事件。
- `TD-003_COMPLETE`：写通话/重试事实；成功进入有效分支，未耗尽推进窗口，耗尽回公海。
- `TD-004_COMPLETE`：本轮只保证由有效分支生成并具备可处理表单；后续5天循环属于下一设计。

## 8. 前端设计

### 8.1 复用组件

继续使用：

- Todo列表、详情、操作动作和审计时间线；
- `TodoDynamicForm`；
- 文件中心选择、材料清单、预览和下载；
- 业务详情中的 `BusinessTodoSummary`。

### 8.2 新增或扩展页面

1. 线索列表：
   - 来源标签只读区；
   - 待确认标签筛选；
   - 单条和批量确认；
   - 首联、复核、重试、Dead-Pool状态。
2. 首联处理抽屉：
   - 电话号码和线索摘要；
   - 通话记录时间线；
   - 人工补录通话；
   - 录音/截图凭证；
   - 三选一结果；
   - 有效时显示姓名、城市、诉求、是否到所。
3. 疑似无效复核台：
   - 无效原因、销售说明、联系证据；
   - 确认无效/误判有效；
   - SLA剩余时间和默认通过提示。
4. 重试时间线：
   - T0/T+1/T+2全部窗口；
   - 当前窗口、已拨次数、下一窗口；
   - 联系成功和耗尽状态。
5. Dead-Pool：
   - 独立菜单、查询权限和详情；
   - 不显示普通公海领取按钮；
   - 恢复需独立权限和原因。
6. 轮转策略配置：
   - 销售组、渠道、业务类型、候选人、顺序、重试规则；
   - 当前游标只读展示；
   - 请假和委托状态。

## 9. 权限

新增最小权限：

```text
lead:tag:confirm
lead:first-contact:handle
lead:invalid-review:list
lead:invalid-review:handle
lead:retry:list
lead:retry:handle
lead:dead-pool:list
lead:dead-pool:restore
lead:assignment-policy:list
lead:assignment-policy:edit
lead:call-record:add
lead:call-record:view
```

销售只能处理本人首联和重试；主管只能查看负责范围内的复核与超时事项；Dead-Pool恢复和轮转策略修改单独授权。

## 10. 错误与一致性

稳定错误码：

```text
TODO_EVENT_PAYLOAD_INVALID
TODO_OWNER_UNRESOLVED
TODO_ROUTE_TARGET_INVALID
TODO_SCHEDULE_WINDOW_CONFLICT
LEAD_FIRST_CONTACT_STATE_INVALID
LEAD_INVALID_REVIEW_STATE_INVALID
LEAD_RETRY_STATE_INVALID
LEAD_DEAD_POOL_ACCESS_DENIED
LEAD_CALL_RECORD_DUPLICATE
```

业务表、动作日志和 Outbox 在一个事务内提交。Todo 完成后业务处理器失败时，Todo状态和动作日志一起回滚。Outbox消费失败保留错误和重试次数。所有条件更新使用当前状态和 `row_version` 防止重复处理。

## 11. 测试与验收

### 11.1 单元测试

- 事件 Schema 拒绝缺失Owner和错误类型；
- 轮转公平性、并发游标、请假跳过、委托、无候选；
- 字典表单选项和非法字典值；
- TD-001、TD-002、TD-003各分支处理器；
- 默认复核字段仍通过完整DoD；
- 窗口计算、取消、重复扫描幂等；
- Dead-Pool与公海隔离。

### 11.2 真实 MySQL 集成测试

- Flyway从v0.15基线执行全部迁移；
- 分配业务事实与 `LEAD_ASSIGNED` Outbox同事务；
- Outbox消费生成正确Owner的TD-001；
- 完成TD-001后只产生一个分支事件和一个下一待办；
- TD-002人工两分支和24小时系统默认分支；
- TD-003各窗口、成功、耗尽和取消；
- 重复事件、动作、路由、窗口扫描均幂等；
- 失败时无半成品业务事实或孤立Todo。

### 11.3 前端测试

- 动态字典加载和缺失阻断；
- 条件字段显隐及必填；
- 通话凭证上传；
- 三类专用页面动作和权限；
- Dead-Pool与公海操作隔离。

### 11.4 浏览器E2E

使用真实后端、MySQL和文件中心完成：

1. 线索标签确认并轮转分配；
2. 生成正确Owner的TD-001；
3. 有效首联补齐四字段并生成TD-004；
4. 疑似无效确认无效进入Dead-Pool；
5. 疑似无效误判后重开TD-001；
6. TD-002到期自动确认；
7. T0失败后进入T+1窗口；
8. T+1联系成功并取消剩余窗口；
9. T+2耗尽回公海；
10. 80/100/150 SLA通知和主管可见；
11. 请假人员被轮转跳过；
12. 重复提交和越权操作被拒绝。

Mock API 页面测试不计作真实业务验收证据。

## 12. 实施分解与完成标准

实施拆为四个可独立审查的切片：

1. Todo Engine事件、Owner、字典、自动动作和窗口调度；
2. 线索表结构、事件、业务服务和完成处理器；
3. TD-001～TD-004正式模板、路由和发布迁移；
4. 前端页面、真实MySQL集成和浏览器E2E。

只有以下条件全部满足才算完成：

- TD-001、TD-002、TD-003、TD-004存在已发布版本，目录不再是BLOCKED；
- 当前分配事件可以生成有明确Owner的TD-001；
- 三个首联结果分支都完成业务事实、下一待办和审计闭环；
- T0/T+1/T+2按配置运行且幂等；
- Dead-Pool与公海、回收站隔离；
- 所有新增字典字段使用受控下拉且后端复核；
- 后端单元测试、真实MySQL测试、前端测试、生产构建和浏览器E2E全部通过；
- 现有无关功能回归测试无新增失败。
