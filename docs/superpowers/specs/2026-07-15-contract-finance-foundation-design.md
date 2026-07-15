# 合同与收费链路基础治理设计

> 基线：`refactor/lead-customer-foundation`，基于 `V0.17`。
> 范围：客户 → 合同 → 审批 → 签署 → 缴费。
> 约束：只治理现有代码，不新增报价、冲突审查、催收、风险代理或 Todo 模板等 v0.2 业务功能。

## 1. 目标与边界

本子项目把现有合同和收费模块改造成可被 Todo Engine 安全编排的业务事实层。业务 Service 仍负责合同、审批、签署、收费和开票事实；Todo Engine 后续只调用这些稳定入口并消费事务型 Outbox 事件。

完成后必须满足：

- 合同从客户创建、提交审批、审批、签署到确认收款形成可测试的现有业务链路；
- 每个状态变化都使用条件更新，拒绝并发覆盖和重复操作；
- 事件与业务事实、动作记录处于同一事务，幂等键由稳定业务标识组成；
- 所有写接口使用强类型命令对象，原 URL、权限编码和 JSON 字段不变；
- `BizContractServiceImpl` 仅承担兼容 Facade，不直接依赖 Mapper；
- 财务模块复用合同收费和开票服务，不复制业务规则；
- 不新增 v0.2 页面、表、事件类型或模板。

下一子项目单独治理“缴费 → 转案 → 案管接收”，避免合同事务与案件接收事务耦合在同一批改造中。

## 2. 当前证据与问题

当前代码已经拆出 `ContractCommandService`、`ContractLifecycleService`、`ContractPaymentService`、`ContractInvoiceService`、`ContractFeePlanService`、`ContractAttachmentService` 等服务，但仍有以下基础缺口：

1. `BizContractServiceImpl` 约 318 行，直接依赖 `BizContractMapper` 和 `BizCustomerMapper`，包含详情权限、导入编排和客户解析逻辑。
2. `ContractQueryService`、Facade 和各命令服务分别实现合同访问判断，权限字符串与当前用户读取分散。
3. 合同编号规则、模板、收费计划和附件共 6 个 Controller 写接口仍接收 `Map<String,Object>`；合同新增、修改仍直接接收持久化实体。
4. `CONTRACT_SUBMITTED`、`CONTRACT_APPROVED`、`CONTRACT_SIGNED`、`PAYMENT_CONFIRMED`、`PAYMENT_REJECTED`、`INVOICE_HANDLED` 的幂等键包含随机 UUID，重复请求无法稳定去重。
5. 审批表已有自增 `approval_id`，附件表已有自增 `attachment_id`，但合同状态日志插入没有回传 `log_id`，导致状态动作缺少稳定事件标识。
6. 收款和开票已使用条件更新，但事件没有绑定具体动作记录；合同签署上传附件和事件的标识也未绑定本次状态日志。
7. `FinanceCommandService` 正确复用合同收费服务，但缺少跨入口一致性的集成测试，不能证明合同页面和财务页面处理同一收费计划时行为一致。
8. 现有合同、财务页面可以保留；本轮只补接口契约和关键链路 E2E，不进行视觉重做。

## 3. 方案选择

### 3.1 采用：分段治理

先治理客户到缴费，再治理转案到案管接收。每个子项目拥有独立事务边界、自动化门禁和提交历史。这样既保留现有系统资产，又能按销售链路顺序逐段建立 Todo Engine 可调用的稳定入口。

### 3.2 不采用：最小补丁

只替换 UUID 和少量 Map 虽然改动较小，但 Facade、权限和导入编排仍然分散，Todo Engine 接入时会再次重构。

### 3.3 不采用：整体重写

一次性重写合同、财务、转案和案管会扩大回归范围，并把多个业务事务混为一个交付单元，不符合当前“先治理、不开发新业务”的约束。

## 4. 目标架构

```text
BizContractController / BizFinanceController
                │
                ▼
       IBizContractService（兼容接口）
                │
                ▼
       BizContractServiceImpl（纯 Facade）
                │
       ┌────────┼──────────────────────────────┐
       ▼        ▼                              ▼
ContractQuery  ContractCommand / Import     Lifecycle / Fee / File
       │        │                              │
       └────────┴──────────┬───────────────────┘
                           ▼
                ContractAccessPolicy
                           │
                           ▼
                 Mapper + Outbox Publisher
```

核心边界：

- `ContractAccessPolicy`：只负责对象存在性、删除状态和数据范围判断；返回已校验的合同或收费计划上下文。
- `ContractQueryService`：列表、详情、看板和关联列表查询；不复制访问规则。
- `ContractCommandService`：合同新增、修改和删除；复用 `CustomerAccessPolicy` 校验客户。
- `ContractImportService`：Excel 行解析、客户定位、重复合同处理和导入结果汇总。
- `ContractLifecycleService`：提交、审批、签署、归档、作废、终止；本轮重点治理提交、审批和签署事件标识。
- `ContractFeePlanService`：收费计划新增、修改、删除。
- `ContractPaymentService` / `ContractInvoiceService`：确认收款、驳回和开票事实；同时服务合同页面与财务页面。
- `ContractTemplateService` / `ContractNumberService` / `ContractAttachmentService`：强类型写入口和各自校验。
- `BizContractServiceImpl`：只委托上述服务，不保留 Mapper、权限字符串、业务校验或事务编排。

## 5. 强类型命令契约

在 `law-business` 中新增以下命令，字段名保持现有前端 JSON 兼容：

| 命令 | 关键字段 | 校验 |
|---|---|---|
| `ContractCreateCommand` | `contractName/customerId/caseType/signAmount/feeType/signMethod/riskLevel/ownerId/deptId/remark` | 名称、客户、案件类型、金额必填，金额大于 0 |
| `ContractUpdateCommand` | 在新增字段基础上增加 `contractId` | `contractId` 必填；不接受审核、签署和合同目标状态 |
| `ContractNumberRuleUpdateCommand` | `ruleId/ruleName/prefix/datePattern/serialLength/status/remark` | 主键、规则名称、前缀、日期格式、序列长度必填 |
| `ContractTemplateCreateCommand` | `templateName/caseType/fileName/fileUrl/versionNo/status/remark` | 名称、案件类型、文件地址必填 |
| `ContractTemplateUpdateCommand` | 新增 `templateId` | `templateId` 必填 |
| `ContractFeePlanCreateCommand` | `contractId/periodNo/receivableAmount/planReceiveDate/remark` | 合同、期次、应收金额必填，金额大于 0 |
| `ContractFeePlanUpdateCommand` | 新增 `planId` | `planId` 必填；不接受确认、实收和开票状态 |
| `ContractAttachmentCreateCommand` | `contractId/fileName/fileUrl/fileType/fileSize/remark` | 合同、文件名、地址和类型必填 |

现有 `ContractApprovalCommand`、`ContractSignCommand`、`ContractReasonCommand`、`FeeConfirmCommand`、`FeeRejectCommand` 和 `FeeInvoiceCommand` 保留，并补齐长度、金额和允许值校验。查询接口允许继续使用 Map 过滤参数和 Map 聚合结果；Mapper 内部允许由命令转换为 Map，但 Controller 和 Service 接口不得暴露写 Map。

## 6. 访问策略

新增 `ContractAccessPolicy`，依赖 `BizContractMapper` 和 `BusinessActorProvider`：

```java
BizContract requireReadable(Long contractId);
BizContract requireOperable(Long contractId);
ContractFeePlanContext requireFeePlanOperable(Long planId);
Long requireAttachmentOperable(Long attachmentId);
```

`ContractFeePlanContext` 是 `ruoyi-system` 内部不可变记录，字段固定为 `planId`、`contractId`、`contractNo`、`confirmStatus`、`invoiceStatus`、`contractStatus`、`receivableAmount` 和 `receivedAmount`；它替代收费写服务对数据库 Map 键名的直接依赖。`requireAttachmentOperable` 返回附件所属 `contractId`，供删除命令执行同一合同范围校验。

规则：

- 合同不存在或逻辑删除返回 `DATA_NOT_FOUND`；
- 不在数据范围返回 `ACCESS_DENIED`；
- 收费计划和附件必须先解析所属合同，再使用同一合同数据范围；
- 新建或修改合同时调用现有 `CustomerAccessPolicy.requireOperable(customerId)`，不再维护客户权限长字符串；
- 模板和编号规则属于系统配置，继续由 Controller 功能权限控制，不错误套用客户/合同数据范围。

所有详情、修改、删除、提交、审批、签署、收费计划、收款、开票和附件命令都必须经过策略。列表仍使用现有 `@DataScope` 或显式范围参数。

## 7. 事务、并发与动作标识

### 7.1 条件更新

所有状态写入必须同时匹配当前事实：

- 提交：审核状态为待审/退回/拒绝，合同状态为草稿；
- 审批：审核状态为审核中，合同状态与读取时一致；
- 签署：审核通过，签署状态与合同状态与读取时一致；
- 收款：计划确认状态、开票状态和合同状态与读取时一致；
- 开票：计划确认状态、开票状态和合同状态与读取时一致；
- 收费计划修改/删除：合同仍允许编辑且计划状态与读取时一致。

受影响行数为 0 统一抛出 `CONCURRENT_MODIFICATION`。重复完成动作根据场景返回 `DUPLICATE_OPERATION` 或既有结果，不产生新日志和事件。

### 7.2 稳定动作 ID

保留现有表结构，调整 Mapper 使动作记录回传自增主键：

- `biz_contract_approval.approval_id`：审批事件动作 ID；
- `biz_contract_status_log.log_id`：提交、签署、收款、驳回和开票事件动作 ID；
- `biz_contract_attachment.attachment_id`：签署文件或付款凭证附件 ID，仅作为事件载荷，不单独发布新事件。

状态日志插入改为接收内部 `ContractStatusLogRecord`，Java 属性使用 `logId`，MyBatis 使用 `useGeneratedKeys="true" keyProperty="logId"`。禁止通过“查询最新日志”获取 ID，避免并发串号。

### 7.3 稳定幂等键

沿用现有事件类型，不新增事件目录：

```text
CONTRACT_SUBMITTED:{contractId}:{logId}
CONTRACT_APPROVED:{contractId}:{approvalId}
CONTRACT_SIGNED:{contractId}:{logId}
PAYMENT_CONFIRMED:{contractId}:{planId}:{logId}
PAYMENT_REJECTED:{contractId}:{planId}:{logId}
INVOICE_HANDLED:{contractId}:{planId}:{logId}
```

事件载荷统一包含 `schemaVersion=1`、`operatorId`、动作记录 ID 和本次业务结果。状态变更、动作日志、附件和 Outbox 写入由同一 `@Transactional` 服务完成；任一步失败全部回滚。

## 8. 业务数据流

### 8.1 客户创建合同

1. Controller 校验 `ContractCreateCommand`。
2. `CustomerAccessPolicy` 验证客户存在、有效且当前用户可操作。
3. `ContractNumberService` 生成合同编号。
4. 插入合同和创建状态日志。
5. 本轮不新增合同创建事件；后续 Todo 触发从既有 `CONTRACT_SUBMITTED` 开始。

### 8.2 提交与审批

1. `ContractAccessPolicy` 返回当前合同快照。
2. 条件更新审核状态。
3. 插入状态日志或审批记录并取得自增 ID。
4. 用动作 ID 构造稳定事件并写入 Outbox。

审批拒绝或退回继续写业务事实与记录，但沿用当前行为，不发布新的 v0.2 事件类型。

### 8.3 签署

1. 校验审批通过、签署状态和合同状态。
2. 条件更新签署及合同状态。
3. 如有签署文件，插入附件并取得附件 ID。
4. 插入状态日志并取得状态日志 ID。
5. 发布 `CONTRACT_SIGNED`，载荷包含签署状态和附件 ID。

### 8.4 缴费与开票

1. 合同页面或财务页面调用同一合同收费服务。
2. 策略校验收费计划所属合同的数据范围。
3. 校验金额、付款方式或开票状态。
4. 条件更新收费计划。
5. 插入状态日志并取得动作 ID；付款凭证作为附件写入。
6. 发布现有收款/驳回/开票事件。

## 9. 错误处理

统一使用现有业务码：

| 场景 | 业务码 |
|---|---|
| 参数缺失、金额或字典值非法 | `VALIDATION_FAILED` |
| 合同、计划、附件不存在 | `DATA_NOT_FOUND` |
| 不在数据范围 | `ACCESS_DENIED` |
| 前置业务事实不满足 | `PRECONDITION_FAILED` |
| 当前状态不允许动作 | `STATE_CONFLICT` |
| 同一动作已经完成 | `DUPLICATE_OPERATION` |
| 条件更新失败 | `CONCURRENT_MODIFICATION` |

Controller 只负责 Bean Validation 和功能权限；Service 负责对象权限、业务状态和事务。异常消息保持中文，前端根据业务码决定刷新、提示或禁止重试。

## 10. 兼容性

- 保持 `/contract/**`、`/finance/**` URL、HTTP 方法和权限编码不变；
- 保持现有 JSON 字段名和 `AjaxResult` / `TableDataInfo` 响应结构；
- 前端 API 函数仅增加 JSDoc 类型，不改变调用方式；
- 查询 Map 和 Mapper 内部 Map 暂时保留，写 Map 不再穿透 Controller/接口层；
- 不修改已发布 Todo 模板版本；
- 不新增数据库表或 v0.2 字段；只调整既有 Mapper 的生成主键回传和条件更新。

## 11. 测试与质量门禁

### 11.1 单元测试

- `ContractAccessPolicyTest`：不存在、删除、越权、收费计划跨合同、附件跨合同；
- 命令 DTO Validation 测试：必填、金额、长度和非法状态输入；
- `ContractCommandServiceTest`：客户越权、创建默认值、禁止前端写状态、并发修改；
- `ContractLifecycleServiceTest`：提交、审批、签署、重复动作、条件更新失败、事务事件键；
- `ContractFeePlanServiceTest`：新增、修改、删除和不可编辑状态；
- `ContractPaymentServiceTest` / `ContractInvoiceServiceTest`：部分收款、补齐、重复确认、越权、稳定幂等键；
- `ContractImportServiceTest`：客户不存在、重名、重复合同、更新模式和整批回滚策略。

### 11.2 集成与接口测试

- `CustomerContractPaymentFlowTest`：客户 → 合同 → 提交 → 审批 → 签署 → 收款；断言业务事实、日志和每种 Outbox 事件各一条；
- 重复提交、审批、签署和收款不产生重复动作或事件；
- 故意让 Outbox 写入失败，断言对应业务更新、日志和附件全部回滚；
- MockMvc 验证原 URL/JSON 兼容并在非法请求时不进入 Service；
- 结构守卫验证合同 Facade 不超过 150 行且没有 Mapper 字段。

### 11.3 前端与全量门禁

- Playwright 覆盖客户进入合同、提交、审批、签署和收费确认入口；
- 财务页面确认同一收费计划仍调用 `/finance/payment/confirm`，最终复用同一合同服务；
- `mvn --batch-mode --no-transfer-progress clean verify`；
- `npm --prefix ruoyi-ui run test:todo`；
- 前端生产构建；
- `npm --prefix ruoyi-ui run test:e2e`；
- 数据库迁移测试在 CI 测试库执行；本轮无迁移时必须证明既有迁移未受影响。

## 12. 完成门禁

只有以下证据全部成立，本子项目才可标记完成：

- 合同 Facade ≤150 行且无 Mapper 依赖；
- 合同全部写接口使用强类型 DTO；
- 合同、收费计划和附件写操作执行统一对象访问策略；
- 提交、审批、签署、收款和开票全部使用条件更新；
- 所有现有事件幂等键不含 UUID，并绑定已持久化动作 ID；
- 重复或并发请求不产生重复审批、收款、附件或事件；
- 业务事实、动作记录、附件和 Outbox 事务一致；
- 合同页面与财务页面的收费规则只有一个实现来源；
- 原 URL、权限和 JSON 字段兼容；
- 单元、集成、MockMvc、构建和 E2E 门禁全部通过；
- 变更范围不存在任何 v0.2 新业务功能。

## 13. 后续入口

完成本设计后，下一治理子项目为“缴费 → 转案 → 案管接收”，重点处理案件生成条件、转案材料事实、转案/接收稳定事件、案管访问策略、强类型命令和跨模块 E2E，不在本设计中提前实现。
