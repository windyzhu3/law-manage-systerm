# 线索待办模板 P0/P1 配置闭环加固设计

**日期：** 2026-07-29

**代码基线：** `runtime/startup-wiring-fix` / `e9d6cf97`

**产品范围：** Todo Engine 七步模板配置旅程、线索 `TD-001` 首联模板、字段资源、负责人、路由、模拟发布和配置健康度

**批准范围：** 完成上一轮审计定义的 P0-1 至 P0-6，以及 P1 的全部降复杂度优化；所有结果必须经单元测试、MySQL 集成测试、前端契约测试、生产构建和真实浏览器验收。

## 1. 当前事实

当前 `TD-001` 可编辑草稿存在五类阻断：

1. 同一字段编码跨多个事件合并，正确中文名称可被“业务字段”占位名称覆盖。
2. `assignmentId NE` 没有比较值，页面显示 `undefined`，后端按 integer 类型拒绝。
3. `operatorId` 被错误列入负责人字段；切换负责人策略会清空并立即保存先前选择。
4. 后续路由的“业务结果”是自由文本，未与 `contactResult` 字典值绑定。
5. 当前草稿哈希与历史模拟证据不一致；样例加载、语义显示和修复定位不足，使用户无法顺利重跑三个必测场景。

这些问题不是独立表单缺陷，而是资源元数据、编辑状态、规范定义、预检和场景证据未使用同一套可执行契约。

## 2. 完成目标

### 2.1 P0

1. 事件字段使用事件版本级身份，跨事件不得互相覆盖中文名称、类型、必填和语义。
2. 可选字段具有中文名称、业务说明、示例、语义类型、选项来源和负责人资格。
3. 触发条件不允许保存缺少比较值的有值操作符；无值判断使用 `EMPTY/NOT_EMPTY/EXISTS/NOT_EXISTS`。
4. `LEAD_ASSIGNED.assignmentId` 的冗余非空条件从 `TD-001` 新草稿中移除。
5. 负责人候选严格服从事件的 `owner_field_paths_json`；`operatorId` 不可作为首联负责人。
6. 负责人策略切换不丢失各策略草稿；正式应用后才写入规范定义。
7. 路由结果由完成字段和字典值驱动，`TD-001` 自动配置三个标准分支。
8. 只读样例一键选择并装载；人员、部门和字典显示中文业务值。
9. 发布预检错误使用中文，能跳到准确步骤和字段。
10. 当前定义哈希下 `TD001_VALID`、`TD001_SUSPECT_INVALID`、`TD001_UNREACHABLE` 全部通过，且不写运行时和业务表。
11. 存量修复创建新的可发布草稿，不修改不可变发布版本，不复用旧证据。

### 2.2 P1

1. 建立跨步骤依赖图：事件、字段、DoD、路由、场景和证据的变化能准确使下游状态失效。
2. 修改完成结果字典或路由字段时，页面显示影响范围并提供自动同步。
3. 为首联模板提供“一键应用推荐路由”，普通用户不填写技术表达式。
4. 负责人配置支持明确的主负责人和兜底解析顺序；候选池和抄送保持独立语义。
5. 页面展示草稿哈希变化、场景证据状态和失效原因。
6. 七步状态由与服务端编译器同源的校验结果决定，不再出现“步骤已完成、发布才报错”。
7. 窄屏旅程导航可滚动且当前步骤始终可见；状态不只依赖颜色表达。

## 3. 核心架构

### 3.1 事件字段身份

事件 Schema 字段使用以下稳定身份：

```text
businessType + eventType + payloadVersion + path
```

资源响应保留 `code=path` 供规范表达式使用，同时增加：

```text
fieldKey
name
description
example
semanticType
optionSource
dictType
eventRole
ownerEligible
sourceEvent
sourcePayloadVersion
```

统一业务字段资源可以补充展示元数据，但不能覆盖其他事件版本的机器事实。名称优先级为：

```text
当前事件版本中文标题
→ 当前事件版本治理名称
→ 统一业务字段中文名称
→ 未治理占位名称
```

“业务字段”属于未治理占位名称，不能覆盖任何有效中文标题。

### 3.2 条件契约

操作符分为：

```text
有值：EQ、NE、IN、NOT_IN、GT、GTE、LT、LTE
无值：EXISTS、NOT_EXISTS、EMPTY、NOT_EMPTY
```

前端、草稿保存、旅程健康检查和发布编译都调用同一语义：

- 有值操作符必须有与字段类型匹配的值。
- `IN/NOT_IN` 必须是非空集合。
- 无值操作符必须保存 `value=null`。
- 必填事件字段默认不推荐再次配置非空条件。
- 自然语言摘要只使用中文标签和值，不渲染 `undefined/null`。

### 3.3 负责人契约

负责人事件字段必须同时满足：

1. 在当前事件和 Payload 版本中存在。
2. 类型可以解析为用户。
3. 位于事件声明的 `owner_field_paths_json`，或显式 `ownerEligible=true`。

编辑器维护 `strategyDrafts`，按策略缓存未应用值：

```text
EVENT_OWNER.field
BUSINESS_OWNER
ROLE.roleKey
USER.userId
CANDIDATE_POOL.roleKey
```

切换只改变正在查看的草稿；点击“应用此规则”才更新模板定义。主负责人和兜底负责人分别展示，避免把多个负责人误解为同时指派。

### 3.4 类型化业务路由

业务路由增加：

```json
{
  "resultField": "contactResult",
  "resultFieldLabel": "首联结果",
  "outcomes": [
    {
      "resultValue": "VALID",
      "resultLabel": "有效",
      "resultType": "NEXT",
      "targetVersionId": 79
    }
  ]
}
```

`label` 不再是用户输入的可执行事实。系统根据 `resultField + resultValue` 生成路由条件和业务化句子：

```text
当首联结果为有效时，创建5天实质进展待办
```

TD-001 推荐路由固定为：

```text
VALID            → TD-004
SUSPECT_INVALID  → TD-002
UNREACHABLE      → TD-003
```

保存前校验枚举覆盖、重复值、目标存在、业务类型兼容、默认分支和不可达分支。

### 3.5 模拟与证据

“加载只读样例”执行：

```text
搜索样例 → 自动选中 → 自动装载 Payload → 自动解析显示值
```

场景卡同时显示中文名称和目标模板名称，技术编码放在次要位置。场景预设值通过语义选项目录解析，`VALID` 默认显示为“有效”。

证据状态分为：

```text
未运行
已通过
未通过
草稿已变化
场景版本已变化
已过期
```

批量验证始终针对当前 `versionId + definitionHash + scenarioVersion`。旧证据只可审计，不可复制到新草稿。

### 3.6 同源健康度与依赖图

服务端返回每步问题：

```text
code
stepCode
fieldPath
severity
messageKey
messageArgs
repairAction
```

前端本地校验仅用于即时输入提示，不能覆盖服务端状态。自动保存完成后重新获取权威步骤状态。

依赖关系：

```text
EVENT → TRIGGER → OWNER
EVENT/FIELD → DOD
DOD completion fields → ROUTING
EVENT/OWNER/DOD/SLA/ROUTING → SIMULATION
任何可执行定义变化 → 当前草稿模拟证据失效
```

## 4. 数据迁移

新增 Flyway 迁移，不修改已执行脚本：

1. 为 `LEAD_ASSIGNED` 补充字段说明、事件角色和负责人资格。
2. 将静态样例用户替换为能在目标环境解析的系统默认策略；模拟时动态绑定当前有效测试用户。
3. 新增首联推荐路由资源。
4. 复制当前 TD-001 可编辑草稿为修复草稿，并应用：
   - 空触发条件；
   - `EVENT_OWNER.ownerId`；
   - 三条类型化路由；
   - 现有 DoD、SLA、自动动作和 UI Schema。
5. 保留所有发布版本和历史证据，不执行删除或伪造通过记录。

迁移必须幂等，并在空库初始化、已有 v0.2 数据库升级和重复启动场景中验证。

## 5. 错误和中文化

后端继续使用稳定错误码，前端使用统一目录显示中文：

| 错误码 | 中文主文案 | 修复位置 |
|---|---|---|
| `TODO_CONDITION_VALUE_TYPE_INVALID` | 条件值与“{field}”字段类型不匹配 | TRIGGER |
| `TODO_CONDITION_VALUE_REQUIRED` | 请为“{field}”选择或填写比较值 | TRIGGER |
| `TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE` | 还有必测场景未通过：{scenarioNames} | SIMULATION_PUBLISH |
| `TODO_OWNER_FIELD_NOT_ELIGIBLE` | “{field}”不是当前事件声明的负责人字段 | OWNER |
| `TODO_ROUTING_OUTCOME_INCOMPLETE` | “{result}”尚未设置后续待办 | ROUTING |

技术详情可展示错误码、字段路径和请求ID，但不作为主文案。

## 6. UI 与交互

延续现有深海军蓝、暖金、白色和浅冷灰设计，不引入新视觉体系。

重点变化：

- 字段下拉显示中文名称，第二行显示业务说明，技术编码仅在高级信息中展示。
- 触发条件空值操作符不显示值控件。
- 负责人策略卡切换不触发保存；显示“未应用修改”。
- 路由使用“当……时→……”句式和可选择的结果值。
- 第七步一键载入样例，场景卡显示中文预期目标。
- 错误卡显示影响、修复步骤和“一键修复/去修复”。
- 旅程导航在窄屏可横向滚动并自动定位当前步骤。

## 7. 测试与验收

### 7.1 后端

- 事件范围字段名称不会被其他事件的占位名称覆盖。
- 负责人字段只包含 `ownerId`。
- 条件缺值返回稳定错误码和正确步骤。
- 路由结果生成正确的条件图。
- 推荐路由覆盖三个首联字典值。
- 证据仅对完全匹配的哈希和场景版本有效。
- 存量迁移不修改发布版本和历史证据。

### 7.2 前端

- `undefined/null` 不出现在条件摘要。
- 切换负责人策略再返回时，原草稿仍存在。
- 路由结果使用字典下拉并生成自然语言。
- 样例按钮自动选择和装载。
- `VALID` 显示“有效”。
- 英文预检错误显示为中文并跳转准确步骤。
- 下游依赖变化显示影响并使模拟状态失效。

### 7.3 MySQL 和真实浏览器

完整验收路径：

```text
打开 TD-001 修复草稿
→ 确认 LEAD_ASSIGNED 字段全部有中文含义
→ 确认触发条件为空且没有 undefined
→ 选择事件负责人 ownerId
→ 切换到指定人员再切回，ownerId 草稿仍存在
→ 应用首联推荐路由
→ 一键加载只读样例
→ 确认人员、部门和首联结果显示中文
→ 批量运行三个场景
→ 确认实际下一待办分别为 TD-004、TD-002、TD-003
→ 发布预检无阻塞
```

验收还必须证明：

- 浏览器控制台无错误。
- 页面无乱码。
- `todo_instance`、`todo_action_log`、`business_event` 和线索业务表在模拟前后不变。
- `todo_simulation_evidence` 中三条证据属于同一当前哈希。
- 后端 `mvn clean verify`、前端全部 Todo 契约测试和生产构建通过。

## 8. 非目标

- 不重写 Todo Engine 运行时。
- 不升级 Vue 3。
- 不引入任意脚本或通用低代码 DSL。
- 不修改不可变发布版本。
- 不把本轮范围扩展到合同、案件、案管和财务模板的业务内容。

