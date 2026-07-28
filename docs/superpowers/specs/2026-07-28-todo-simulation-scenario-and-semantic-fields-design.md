# Todo 模拟场景与语义字段优化设计

**日期：** 2026-07-28

**代码基线：** `runtime/startup-wiring-fix` / `eb2983a5709f4a5f0c69c828d8d99298fc521646`

**产品范围：** Todo Engine 模板配置旅程第七步“模拟发布”及其依赖的字段资源、只读样例、名称解析、发布预检

**已确认方案：** 方案 C——默认验证待办创建，同时提供完整办理分支场景

## 1. 背景与问题

当前模板配置旅程能够选择真实业务对象或只读样例，并从业务对象、事件样例、系统默认值和手工覆盖中装载模拟 Payload。但是，以 `TD-001` 首联待办为例，加载 `LEAD_ASSIGNED` 只读样例后，页面会同时展示：

1. 创建 TD-001 所需的事件输入字段；
2. 完成 TD-001 时由员工填写的字段；
3. TD-002 疑似无效复核使用的字段；
4. TD-003 重试待办使用的字段；
5. 同一 `LEAD` 业务类型下其他通用字段。

这些字段中只要没有值就统一标记为“待补充”，造成两个错误认知：

- 用户误以为必须在创建待办前手工填写所有字段；
- 用户无法判断字段对应业务事件、负责人、DoD、SLA还是后续路由。

当前字段值还存在业务化展示不足：人员、部门、角色和字典字段可能直接显示内部 ID 或编码，编辑时也可能退化为普通输入框。业务管理员需要理解 `ownerId=11`、`ownerDeptId=103`、`contactResult=VALID` 等技术值，不符合已确认的配置中心设计目标。

## 2. 目标

本次优化需要同时实现以下结果：

1. 只读样例默认只展示当前模板实际引用的字段。
2. 明确区分事件输入、触发条件、负责人、完成输入、SLA参数、路由输入和高级字段。
3. 只有当前阶段真正必需但没有值的字段才显示为阻塞项。
4. 人员、部门、岗位、角色、字典和业务引用默认展示业务名称，不直接展示裸 ID 或编码。
5. 选择人员、部门、角色和字典时使用受控选择器，不允许自由填写内部值。
6. TD-001 提供“有效首联、疑似无效、未接通”三个可重复运行的标准场景。
7. 用户既能验证 TD-001 是否正确创建，也能验证办理、DoD、业务回写和下一待办路由。
8. 发布前必须证明当前草稿哈希下的必测场景全部通过。
9. 模拟过程只读，不创建真实待办，不修改业务表，不写业务事件 Outbox。
10. 设计形成通用能力，后续合同、案件、案管和财务模板可以复用。

## 3. 非目标

本次不建设通用场景 DSL 或任意脚本执行器，不允许在页面上传或运行代码。以下内容不属于本轮：

- 外呼供应商真实呼叫；
- 生产业务数据批量生成；
- 对 Todo Engine 运行时路由和完成语义的重新设计；
- 全量替换现有字典、组织和权限体系；
- 将所有历史模板一次性补齐标准场景；
- 在模拟中产生可领取的真实待办。

## 4. 产品模型

配置第七步拆分为两个逐级验证区域。

### 4.1 待办创建验证

验证从业务事件到待办卡片生成之前的全部配置：

```text
业务对象或只读样例
→ 事件输入
→ 触发条件
→ 负责人解析
→ SLA计算
→ 待办卡片预览
```

该区域展示：

- 测试对象；
- 事件输入和来源；
- 负责人名称、部门和兜底策略；
- SLA日历、到期时间及80%/100%/150%节点；
- 待办标题、优先级、关联业务和预计负责人。

事件输入默认只读。高级调试人员可以在折叠区域对非敏感字段做本次模拟覆盖，覆盖值不写回业务对象或事件资源。

### 4.2 完整办理场景

创建验证通过后，用户选择一个标准场景，模拟员工处理当前待办：

```text
模拟员工办理
→ DoD校验
→ 模拟业务处理器
→ 业务结果
→ 路由判断
→ 下一张待办预览
```

TD-001 必须提供：

| 场景 | 系统预设 | 用户填写 | 预期结果 |
|---|---|---|---|
| 有效首联 | `contactResult=VALID`、联系时间为模拟时间 | 姓名、城市、诉求、是否到所、联系凭证 | 完成 TD-001，下一待办为 TD-004 |
| 疑似无效 | `contactResult=SUSPECT_INVALID`、联系时间为模拟时间 | 场景说明、联系凭证 | 完成 TD-001，下一待办为 TD-002 |
| 未接通 | `contactResult=UNREACHABLE`、联系时间为模拟时间 | 拨打凭证 | 完成 TD-001，下一待办为 TD-003并计算下一重试窗口 |

`reviewResult`、`reviewOpinion` 属于 TD-002；`attemptStage`、`attemptCount` 属于 TD-003。它们不得作为 TD-001 创建验证的默认待补充项。

## 5. 字段归属算法

### 5.1 字段用途

后端从当前模板规范定义中提取字段引用，而不是把业务类型下所有字段直接返回。字段用途包括：

| 用途 | 来源 |
|---|---|
| `EVENT_INPUT` | 所选事件版本的 Payload Schema |
| `TRIGGER_INPUT` | 触发条件表达式引用字段 |
| `OWNER_INPUT` | 负责人策略和兜底策略引用字段 |
| `COMPLETION_INPUT` | DoD、员工表单和条件必填规则 |
| `SLA_INPUT` | SLA动态时长、起算或日历引用字段 |
| `ROUTING_INPUT` | 路由条件、业务结果和下一待办选择 |
| `ADVANCED` | 当前模板未引用但允许高级调试覆盖的字段 |

字段可以具有多个用途。例如 `contactResult` 同时用于完成校验和路由，因此返回两个 `usages`，但只渲染一份场景输入。

### 5.2 必填判定

“待补充”只用于当前验证阶段的必填缺失：

- 创建验证：只计算事件 Schema required、触发和负责人解析必需字段；
- 场景验证：计算所选场景的 DoD required、条件必填、材料和路由必需字段；
- 未被当前阶段引用的字段不参与覆盖率；
- 可选字段显示“可选”，不显示“待补充”；
- 系统计算字段显示“系统计算”，不提供手工输入；
- 敏感字段显示脱敏值，不能通过手工覆盖泄露。

### 5.3 装载优先级

最终模拟值按以下顺序合并，后者覆盖前者：

```text
业务对象值或事件样例
→ 系统默认值
→ 标准场景预设
→ 本次手工覆盖
```

每个字段必须保留来源标签：

- `BUSINESS_OBJECT`
- `EVENT_SAMPLE`
- `SYSTEM_DEFAULT`
- `SCENARIO_PRESET`
- `MANUAL_OVERRIDE`
- `MISSING`

## 6. 字段语义与名称解析

### 6.1 语义元数据

字段资源在现有 `todo_configuration_resource_item.value_json` 中增加：

```json
{
  "type": "integer",
  "required": true,
  "semanticType": "USER_ID",
  "optionSource": "SYSTEM_USER",
  "displayPattern": "{userName} · {deptName}",
  "allowInactive": false
}
```

字典字段示例：

```json
{
  "type": "string",
  "semanticType": "DICT",
  "dictType": "law_lead_contact_result",
  "allowedValues": ["VALID", "SUSPECT_INVALID", "UNREACHABLE"]
}
```

事件 Payload Schema 同步支持：

- `x-semantic-type`
- `x-option-source`
- `x-dict-type`
- `x-sensitive`

### 6.2 支持的语义

| 语义 | 展示 | 选择方式 |
|---|---|---|
| `USER_ID` | 人员姓名、部门、岗位、状态 | 权限范围内人员远程搜索 |
| `DEPT_ID` | 完整部门路径 | 部门树 |
| `POST_ID` | 岗位名称 | 岗位选择器 |
| `ROLE_KEY` | 角色名称 | 角色选择器 |
| `DICT` | 字典标签 | 已启用字典下拉 |
| `BOOLEAN` | 是/否 | 是/否选择器 |
| `DATETIME` | 本地化日期时间 | 日期时间选择器 |
| `BUSINESS_REF` | 业务编号和名称 | 有权限的业务对象选择器 |
| `PLAIN_VALUE` | 格式化文本或数字 | 对应类型控件 |

模板和事件内部继续保存稳定 ID 或编码。名称只用于展示；模拟证据可保存脱敏显示快照。人员、部门、角色或字典失效时，页面显示“原配置对象已失效”并阻止发布，不得降级显示裸 ID。

### 6.3 解析接口

新增统一 `TodoFieldDisplayResolver`，按语义拆分实现：

```text
TodoFieldDisplayResolver
├── UserDisplayResolver
├── DepartmentDisplayResolver
├── PostDisplayResolver
├── RoleDisplayResolver
├── DictDisplayResolver
├── BusinessReferenceResolver
└── DefaultValueResolver
```

解析器接收 `semanticType + rawValue + fieldMetadata + actor`，返回：

```text
displayValue
displayMeta
selectable
invalidReason
restricted
```

解析必须批量执行，禁止对每个字段单独查询人员或部门。组织和业务引用继续执行当前用户的数据权限；无权查看的引用只显示“受限对象”。

## 7. 标准场景资源

复用 `todo_configuration_resource_item`，增加资源类型 `SIMULATION_SCENARIO`。场景值包含：

```json
{
  "scenarioCode": "TD001_VALID",
  "templateCode": "TD-001",
  "scenarioName": "有效首联",
  "completionPayload": {
    "contactResult": "VALID",
    "contactedAt": "${SIMULATION_NOW}"
  },
  "editableFields": ["name", "city", "demand", "visited"],
  "requiredMaterials": ["CONTACT_PROOF"],
  "expectedNextTemplateCode": "TD-004",
  "sortOrder": 10
}
```

场景资源受版本、状态和引用治理。场景治理首版只通过 Flyway 和资源中心业务化表单维护，不支持脚本和任意表达式。

## 8. 接口设计

### 8.1 扩展现有装载接口

保留：

```http
POST /todo/config/templates/{templateId}/journey/payload
```

响应扩展为：

```text
businessObject
eventInput
creationDependencies
completionFields
routingFields
advancedFields
coverage
blockingIssues
```

字段返回：

```json
{
  "path": "ownerId",
  "label": "线索负责人",
  "rawValue": 11,
  "displayValue": "张三",
  "semanticType": "USER_ID",
  "displayMeta": {
    "deptName": "销售一部",
    "postName": "销售顾问",
    "status": "ACTIVE"
  },
  "source": "EVENT_SAMPLE",
  "required": true,
  "missing": false,
  "editable": true,
  "usages": [
    {
      "stage": "OWNER_INPUT",
      "stepCode": "OWNER",
      "reason": "从事件负责人创建首联待办"
    }
  ]
}
```

### 8.2 新增接口

```http
GET  /todo/config/templates/{templateId}/journey/scenarios
POST /todo/config/templates/{templateId}/journey/scenarios/{scenarioCode}/simulate
POST /todo/config/templates/{templateId}/journey/scenarios/batch-simulate
GET  /todo/config/resources/field-options
```

`field-options` 统一返回字典、人员、部门、岗位、角色和业务对象选项，响应结构固定为 `value + label + meta + disabled + reason`。

单场景和批量模拟必须接收：

- `templateId`
- `versionId`
- `definitionHash`
- `businessType`
- `businessId`
- `scenarioCode`
- `manualOverrides`
- `effectiveAt`
- `requestId`

## 9. 模拟证据与发布门禁

新增 `todo_simulation_evidence`：

```text
evidence_id
template_id
version_id
definition_hash
scenario_code
scenario_version
result_status
input_hash
trace_summary_json
executed_by
executed_time
expire_time
```

限制：

- 不保存真实客户敏感数据；
- 不保存完整 Payload；
- 只保存输入哈希、脱敏轨迹摘要和结果；
- 定义哈希变化后旧证据失效；
- 场景版本变化后必须重跑；
- 重复请求按稳定幂等键返回同一证据；
- 证据不属于运行时 Todo，不可领取或办理。

发布预检增加：

1. 事件 Schema 完整；
2. 字典、人员、部门、岗位和角色引用有效；
3. 必需字段存在可用语义定义；
4. 负责人可解析或有有效兜底；
5. 当前定义哈希下所有必测场景通过；
6. 场景预期路由与实际路由一致；
7. 模拟过程未修改业务表、Todo实例和业务事件表；
8. 警告项已填写发布说明。

## 10. 前端组件

现有 `SimulationPublishStep.vue` 调整为编排组件，拆出：

```text
SimulationPublishWorkbench
├── BusinessObjectSelector
├── CreationValidationPanel
├── EventInputPanel
├── SemanticValueRenderer
├── SemanticOptionSelector
├── TodoCreationPreview
├── ScenarioSelector
├── CompletionFormRenderer
├── ScenarioTrace
├── BatchScenarioGate
└── AdvancedPayloadOverride
```

交互要求：

- 默认不显示内部 ID 和编码；
- 默认不展示当前模板未引用字段；
- 必填、条件必填、系统计算、可选字段使用不同标签；
- 选择场景后只展示该场景需要填写的内容；
- 切换场景时清除不再适用的手工覆盖；
- 草稿、事件资源或场景资源发生变化时立即清除旧模拟结果；
- 错误项提供返回事件、负责人、DoD、SLA或路由步骤的修复入口；
- 高级技术信息默认折叠。

## 11. 异常处理

| 异常 | 行为 |
|---|---|
| 字典类型不存在 | 阻止模拟，跳转资源中心 |
| 字典值停用 | 显示原名称和“已停用”，阻止发布 |
| 人员停用或离职 | 显示姓名和状态，要求重新选择 |
| 引用超出权限 | 显示“受限对象”，不泄露详情 |
| 部门或角色删除 | 显示失效提示，阻止发布 |
| 事件必填字段缺失 | 创建验证失败 |
| 可选字段为空 | 不标记为阻塞 |
| 场景完成字段缺失 | 仅阻塞当前场景 |
| 路由目标不存在 | 当前场景失败 |
| 草稿哈希变化 | 旧模拟和证据失效 |
| 名称解析服务异常 | 显示名称加载失败，不展示裸 ID |
| 模拟检测到真实写入 | 立即失败并记录高危审计 |

错误信息必须包含问题、影响步骤、阻塞级别和修复入口。

## 12. 安全与只读保证

模拟继续使用当前用户的数据权限选择业务对象。业务处理器模拟不得直接调用生产写入接口，采用只读投影或模拟适配器计算预期业务结果。

集成测试在模拟前后对以下对象做快照并断言不变：

- `todo_instance`
- `todo_action_log`
- `business_event`
- `biz_lead`
- 线索跟进、首联、复核、重试和分配相关表

只允许写入 `todo_simulation_evidence` 和必要的配置审计记录。

## 13. 两阶段实施

### 阶段一：字段准确性和业务化展示

后端：

1. 建立字段用途提取和必填判定；
2. 扩展字段语义元数据；
3. 建立批量名称解析层；
4. 扩展 Payload 装载响应；
5. 提供统一字段选项接口；
6. 补齐首联人员、部门、角色和字典语义；
7. 修复全部 `LEAD` 字段进入 TD-001 模拟页面的问题。

前端：

1. 拆分模拟工作台；
2. 建立语义值展示和选择器；
3. 区分事件输入、完成输入和高级字段；
4. 隐藏无关字段和原始 ID；
5. 完成待办创建验证卡片。

阶段一完成标准：

- TD-001 创建字段自动装载；
- 页面不显示 TD-002、TD-003 专属字段；
- 人员、部门、岗位、角色和字典显示名称；
- 字典和组织字段使用受控选择器；
- 事件必填字段覆盖率为100%；
- 真实对象和只读样例都能执行创建验证。

### 阶段二：完整场景和发布门禁

后端：

1. 增加 `SIMULATION_SCENARIO` 资源；
2. 建立 TD-001 三个标准场景；
3. 支持单场景和批量模拟；
4. 增加模拟证据；
5. 将场景证据接入发布预检；
6. 证明模拟过程不写入运行时或业务数据。

前端：

1. 增加三个场景卡片；
2. 动态生成场景完成表单；
3. 展示完整执行轨迹；
4. 对比预期和实际路由；
5. 增加批量验证和发布门禁面板。

阶段二完成标准：

- 有效首联路由到 TD-004；
- 疑似无效路由到 TD-002；
- 未接通路由到 TD-003；
- 三个场景可单独和批量运行；
- 模拟不产生真实 Todo 或业务写入；
- 草稿或场景变化后旧证据失效；
- 三个场景未全部通过时禁止发布。

## 14. 测试与验收

### 14.1 后端单元测试

- 字段用途提取；
- 必填和条件必填判定；
- 语义元数据合并；
- 人员、部门、岗位、角色和字典解析；
- 批量解析和权限过滤；
- 场景预设生成；
- 预期路由判断；
- 证据与定义哈希绑定；
- 失效引用阻止发布。

### 14.2 MySQL集成测试

- `LEAD_ASSIGNED` 样例装载；
- TD-001 只返回当前模板引用字段；
- 三个场景分别路由到 TD-004、TD-002和TD-003；
- 模拟前后运行时表和业务表无变化；
- 无权限用户不能读取业务对象或组织详情；
- 字典停用、人员停用和部门删除后预检失败；
- 重复场景请求不重复生成证据。

### 14.3 前端测试

- 名称替代内部 ID；
- 字典字段使用字典选项；
- 人员和部门远程选择；
- 无关字段不展示；
- 场景切换动态切换表单；
- 单场景和批量场景状态；
- 草稿变化使旧结果失效；
- 错误可返回对应步骤。

### 14.4 Chrome验收

```text
打开 TD-001
→ 加载只读样例
→ 确认创建输入无无关待补充
→ 确认负责人显示姓名和部门
→ 验证 TD-001 创建
→ 分别运行三个场景
→ 批量验证
→ 发布预检
```

验收要求浏览器控制台无错误，页面无乱码，所有选择器可操作，测试轨迹和发布门禁与后端结果一致。

## 15. 实施顺序

```text
字段用途提取
→ 语义元数据
→ 名称解析
→ 通用选择器
→ 创建验证
→ 标准场景资源
→ 场景模拟
→ 模拟证据
→ 发布门禁
→ MySQL与Chrome全流程验收
```
