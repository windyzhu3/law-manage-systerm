# 线索→客户基础治理设计

## 1. 背景与目标

v0.2 最终目标是以待办事件驱动“线索接入 → 分配 → 首联 → 跟进 → 合同 → 审批 → 签署 → 缴费 → 转案 → 案管接收”的销售主链路。当前迭代不实现首联复核、无效分级、周期跟进等 v0.2 新业务，而是先把现有线索和客户模块治理为可靠、可测试、可被 Todo Engine 编排的业务底座。

本设计覆盖当前已经存在的能力：

- 线索新增、编辑、删除、恢复、彻底删除；
- 线索分配、移入公海、公海领取；
- 线索跟进、转化客户；
- 客户新增、编辑、删除、导入；
- 客户联系人、跟进、标签、合并；
- 上述动作的数据权限、状态约束、事务、并发保护和业务事件。

本轮不新增：首联结果模型、疑似无效主管复核、T0/T+1/T+2 重试、无效等级、5天实质进展、Dead-Pool 或任何新的 v0.2 页面与字段。

## 2. 当前问题

- `BizLeadServiceImpl` 同时承担查询、CRUD、分配、公海、跟进、设置、转化和权限判断，约 546 行。
- `BizCustomerServiceImpl` 同时承担查询、CRUD、导入、联系人、跟进、标签、合并和权限判断，约 585 行。
- 客户联系人、跟进、标签和合并等写接口仍使用 `Map<String,Object>`，参数契约只能在运行时发现错误。
- 线索和客户模块只有首联 Todo 校验/处理测试，没有覆盖现有核心 Service 行为的测试集。
- 线索转客户包含客户、联系人、线索状态和 Outbox 事件多个写入点，需要明确一个事务和稳定幂等语义。
- 部分事件幂等键带随机 UUID，只能防止同一 Outbox 记录重复处理，不能防止同一业务动作重复提交。

## 3. 设计原则

1. **业务表是事实来源**：Todo Engine 只编排工作，不能代替线索、客户状态落库。
2. **保持接口兼容**：现有 URL、权限编码、请求字段和响应结构不发生破坏性变化。
3. **先增加安全网再拆分**：每个行为先写失败测试，验证失败原因后实现最小改动。
4. **模块化单体**：按职责拆分 Spring Service，不引入微服务或消息中间件。
5. **事务型事件**：业务状态与 `business_event` 在同一事务写入；消费者负责后续待办生成。
6. **稳定业务幂等**：同一个业务动作使用可重复计算的幂等键；一次性动作由状态条件更新和唯一约束共同保护。
7. **不夹带 v0.2 新业务**：本轮只让现有能力可靠、清晰、可扩展。

## 4. 目标结构

### 4.1 线索服务

```text
com.ruoyi.system.service.lead
├── LeadQueryService          列表、详情、工作台统计、数据范围参数
├── LeadCommandService        新增、编辑、删除、恢复、彻底删除
├── LeadAssignmentService     分配负责人
├── LeadPoolService           移入公海、领取
├── LeadFollowupService       跟进记录和下一跟进时间
└── LeadConversionService     线索转客户事务编排
```

`BizLeadServiceImpl` 保留并实现原 `IBizLeadService`，只作为兼容 Facade 委托给子服务。Controller 和前端无需更换接口。

### 4.2 客户服务

```text
com.ruoyi.system.service.customer
├── CustomerQueryService      列表、详情、工作台、数据范围
├── CustomerCommandService    新增、编辑、删除、导入
├── CustomerContactService    联系人维护
├── CustomerFollowupService   客户跟进
├── CustomerTagService        标签维护与分配
└── CustomerMergeService      主从客户合并
```

`BizCustomerServiceImpl` 保留并实现原 `IBizCustomerService`，只作为兼容 Facade。

### 4.3 共享访问策略

现有功能权限仍由 Controller 的 `@PreAuthorize` 判断。Service 对具体数据再次执行访问策略：

- `LeadAccessPolicy`：查看、编辑、分配、移入公海、领取、跟进、转化；
- `CustomerAccessPolicy`：查看、编辑、联系人、跟进、标签、合并；
- 管理员、全部数据、本部门、本部门及下级、自定义部门、仅本人沿用现有 RuoYi 数据范围；
- 公海线索只允许具备领取权限的有效用户领取；
- Service 不接受前端提供的操作者姓名、部门或目标状态作为可信事实。

访问失败统一抛出 `ServiceException`，错误码为 `ACCESS_DENIED`。

## 5. 命令对象与接口兼容

新增命令对象位于 `law-business`，由 Controller 使用 `@Valid` 校验：

```text
lead/
├── LeadAssignCommand
├── LeadMoveToPoolCommand
└── LeadFollowupCommand

customer/
├── CustomerContactCreateCommand
├── CustomerContactUpdateCommand
├── CustomerFollowupCreateCommand
├── CustomerTagCreateCommand
├── CustomerTagUpdateCommand
├── CustomerTagAssignCommand
└── CustomerMergeCommand
```

请求 JSON 字段沿用现有前端字段。Controller 不再手工从 Map 转换 Long、金额、日期或必填字符串。查询接口可以继续使用查询对象或 Map，因为其参数是可选过滤条件，不承担业务状态写入。

## 6. 线索状态与操作约束

沿用现有 `LeadStatus`，本轮不增加状态值：

| 动作 | 允许状态 | 结果 |
|---|---|---|
| 新增 | 无 | 未分配或待跟进，取决于是否指定有效负责人 |
| 分配 | 未分配、待跟进、跟进中 | 待跟进，更新负责人并记录分配日志 |
| 移入公海 | 待跟进、跟进中 | 未分配，清空负责人并记录原因 |
| 领取 | 未分配且处于公海 | 待跟进，负责人为当前用户 |
| 新增跟进 | 待跟进、跟进中 | 跟进中，更新最近/下次跟进时间 |
| 转客户 | 待跟进、跟进中 | 已转化，记录客户 ID |
| 删除 | 非已转化且未删除 | 逻辑删除 |
| 恢复 | 已逻辑删除 | 恢复原业务状态 |

所有状态变化使用“主键 + 旧状态 + 版本/更新时间”的条件更新。受影响行数为 0 时返回 `CONCURRENT_MODIFICATION`，不得静默覆盖。

## 7. 线索转客户事务

### 7.1 正常流程

```text
锁定或条件读取线索
→ 校验访问权和可转化状态
→ 检查线索尚未关联客户
→ 按手机号/统一信用代码/客户名称检查重复
→ 创建客户
→ 创建主联系人（存在有效联系人信息时）
→ 条件更新线索为已转化并写 customer_id
→ 写入 LEAD_CONVERTED Outbox 事件
→ 事务提交
```

### 7.2 幂等与并发

- 已成功转化且 `customer_id` 存在时，重复请求返回现有客户 ID，不创建第二个客户；
- 相同线索的并发转化只有一个条件更新成功；失败事务回滚其新建客户和联系人；
- `LEAD_CONVERTED` 幂等键固定为 `LEAD_CONVERTED:{leadId}:{customerId}`；
- 客户重复规则沿用现有作用域，手机号和统一信用代码为强匹配，名称匹配按现有业务校验执行；
- 任一写入失败，客户、联系人、线索状态和 Outbox 全部回滚。

## 8. 客户子域约束

### 8.1 联系人

- 必须关联可访问的有效客户；
- 姓名和联系方式按现有校验规则处理；
- 设置主联系人时，以条件更新取消同客户原主联系人；
- 禁止修改其他客户的联系人 ID。

### 8.2 跟进

- 必须关联可访问的有效客户；
- 跟进内容、方式和时间由 DTO 校验；
- 操作人从安全上下文写入；
- 本轮不产生“5天实质进展”等新事件。

### 8.3 标签

- 标签定义写入与客户标签分配分离；
- 分配前校验客户访问权和标签有效性；
- 使用客户 ID + 标签 ID 唯一约束/幂等插入；
- 一次提交以请求中的标签集合为最终状态。

### 8.4 合并

- 主客户和被合并客户不能相同；
- 两者都必须存在、有效且在操作者数据范围内；
- 联系人、跟进、标签和可迁移业务关系在同一事务迁移；
- 被合并客户状态改为 `MERGED` 并记录合并日志；
- 并发合并通过条件更新拒绝第二次处理。

## 9. 事件契约

保留现有事件类型：

- `LEAD_CREATED`
- `LEAD_ASSIGNED`
- `LEAD_MOVED_TO_POOL`
- `LEAD_CLAIMED`
- `LEAD_CONVERTED`

事件载荷必须包含 `schemaVersion=1`、操作者 ID、负责人 ID（适用时）和动作所需业务字段。幂等键规则：

| 事件 | 幂等键 |
|---|---|
| LEAD_CREATED | `LEAD_CREATED:{leadId}` |
| LEAD_ASSIGNED | `LEAD_ASSIGNED:{leadId}:{assignmentLogId}` |
| LEAD_MOVED_TO_POOL | `LEAD_MOVED_TO_POOL:{leadId}:{assignmentLogId}` |
| LEAD_CLAIMED | `LEAD_CLAIMED:{leadId}:{assignmentLogId}` |
| LEAD_CONVERTED | `LEAD_CONVERTED:{leadId}:{customerId}` |

本轮不新增首联或周期跟进事件。事件发布失败必须使业务事务失败。

## 10. 错误契约

| 错误码 | 使用场景 |
|---|---|
| `VALIDATION_FAILED` | DTO或业务字段不合法 |
| `DATA_NOT_FOUND` | 线索、客户、联系人、标签不存在 |
| `ACCESS_DENIED` | 无功能或数据操作权限 |
| `STATE_CONFLICT` | 当前业务状态不允许操作 |
| `PRECONDITION_FAILED` | 负责人、字典、关联对象等前置条件不满足 |
| `CONCURRENT_MODIFICATION` | 条件更新失败、数据已被其他请求改变 |
| `DUPLICATE_SUBMISSION` | 幂等键已被不同请求占用 |

前端继续使用现有 AjaxResult 结构，本轮不调整全局响应协议。

## 11. 测试策略

每项行为遵循红—绿—重构：

### 单元测试

- 分配、移入公海、领取的状态和权限；
- 已转化、已关闭、已删除线索拒绝非法操作；
- 客户联系人、跟进、标签和合并的数据权限；
- DTO必填、长度、格式校验；
- 事件载荷和稳定幂等键。

### 事务与集成测试

- 线索转客户成功时四类数据一起提交；
- 客户或联系人写入失败时无半成品；
- 同一线索重复/并发转化只产生一个客户和一个事件；
- 客户合并失败时所有迁移回滚；
- Controller URL、请求字段和权限编码保持兼容。

### 回归验证

- `mvn clean verify`；
- `npm run test:todo`；
- `npm run build:prod`；
- `npm run test:e2e`；
- Flyway 从 v0.15 基线执行全部迁移。

## 12. 验收标准

- `BizLeadServiceImpl` 和 `BizCustomerServiceImpl` 均降至约 150 行以内，仅保留 Facade 委托；
- 客户模块写接口不再直接接收 `Map<String,Object>`；
- 线索分配、公海、跟进、转化及客户联系人、标签、合并具有自动化测试；
- 线索转客户满足事务、幂等、并发和稳定 Outbox 事件要求；
- 所有现有前端请求字段、Controller URL、权限编码保持兼容；
- 后端、前端、E2E和数据库迁移质量门禁全部通过；
- 提交中不包含任何 v0.2 新业务字段、表、页面或待办模板。
