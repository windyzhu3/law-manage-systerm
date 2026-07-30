# Todo Engine 模拟发布统一准入设计

**日期：** 2026-07-30

**代码基线：** `runtime/startup-wiring-fix` / `0dd4843e`

**适用范围：** Todo Engine 第七步“模拟发布”、模板配置健康度、发布预检、版本差异，以及 `TD-001` 首联待办的完整验证流程。

## 1. 已确认的问题

真实页面和接口跟踪已经确认三个彼此独立、但同时暴露给用户的问题：

1. 完整试运行接口成功后，页面正确提示“当前草稿试运行通过”。
2. 版本列表接口返回蛇形字段 `version_id`，前端只读取 `versionId/id`，因此错误请求：

   ```text
   /todo/definitions/versions/undefined/diff/88
   ```

3. 发布预检依据 `todo_simulation_evidence` 判断三个必要场景，已经返回 `0 项阻塞`；配置健康度却仍读取模板定义中的：

   ```text
   ui.simulationStatus
   ui.simulationDefinitionHash
   ```

   试运行不会修改模板定义，因此这两个字段不会被更新，健康度会永久显示：

   ```text
   A successful simulation of this editable definition is required
   ```

根因不是单个提示文案错误，而是完整试运行、场景证据、发布预检和旅程健康度使用了不同的状态来源。

## 2. 产品决策

模拟发布采用双重门禁，二者必须同时满足：

1. 当前 `versionId + definitionHash` 下，所有标记为 `requiredForPublish` 的必要场景均已通过。
2. 当前 `versionId + definitionHash` 下，至少有一次完整试运行通过。

任一可执行定义、绑定规则或必要场景版本发生变化，旧证据自动失效，不允许复制到新草稿。

只读样例和真实业务对象都可以用于试运行。试运行不得创建运行时待办，不得修改线索、客户、合同、案件或财务数据；写入不可变的模拟证据和审计记录不属于业务数据变更。

## 3. 统一准入模型

新增服务端统一投影 `SimulationReadiness`：

```text
templateId
versionId
definitionHash
requiredScenarioCount
passedScenarioCount
blockingScenarios
fullSimulationPassed
publicationReady
issues
```

状态计算规则：

```text
scenariosReady =
  当前哈希下全部 requiredForPublish 场景存在有效 PASSED 证据

fullSimulationReady =
  当前哈希下存在 FULL_SIMULATION 的有效 PASSED 证据

publicationReady =
  scenariosReady AND fullSimulationReady
```

以下服务必须读取同一个 `SimulationReadiness`：

- 模板旅程七步状态；
- 右侧配置健康度；
- 模板配置工作台；
- 发布预检；
- 发布按钮门禁；
- “去修复”定位。

前端不得自行推断或覆盖服务端准入状态。

## 4. 证据模型

继续复用 append-only 的 `todo_simulation_evidence`，不把运行结果写回模板定义。

### 4.1 必要场景证据

沿用现有记录：

```text
scenario_code = TD001_VALID / TD001_SUSPECT_INVALID / TD001_UNREACHABLE
scenario_version = 场景资源版本
result_status = PASSED / FAILED
```

### 4.2 完整试运行证据

使用保留编码：

```text
scenario_code = FULL_SIMULATION
scenario_version = 1
result_status = PASSED / FAILED
```

完整试运行只有同时满足以下条件才能记录 `PASSED`：

- 运行版本等于当前草稿版本；
- 运行定义哈希等于当前草稿哈希；
- 触发结果为 `MATCHED`；
- 模拟结果不存在 `ERROR/BLOCKER`；
- 返回的 `publishEligible=true`。

完整试运行证据采用独立事务追加。即使使用只读样例，也只写模拟证据和既有脱敏审计，不写业务表或运行时待办表。

## 5. 后端数据流

### 5.1 运行完整试运行

```text
加载业务对象或只读样例
→ 校验当前版本与定义哈希
→ 执行纯模拟
→ 生成脱敏轨迹
→ 追加 FULL_SIMULATION 证据
→ 重新计算 SimulationReadiness
→ 返回模拟结果与统一准入状态
```

### 5.2 发布预检

```text
编译并校验当前定义
→ 计算 SimulationReadiness
→ 合并结构问题、场景问题和完整试运行问题
→ 返回唯一权威的 errors / warnings / definitionHash
```

### 5.3 模板旅程与健康度

`TodoConfigurationJourneyEvaluator` 继续负责事件、触发条件、负责人、完成标准、SLA 和路由的结构检查，但不再读取 `ui.simulationStatus`。

第七步状态由统一准入服务提供：

```text
必要场景未完成 → BLOCKED
完整试运行未完成 → BLOCKED
双重门禁均通过 → COMPLETED
```

配置工作台和模板详情必须使用相同规则，不能出现详情已通过、工作台仍阻塞或反向不一致。

## 6. 前端交互

### 6.1 标准操作顺序

```text
选择真实对象或加载只读样例
→ 批量验证必要场景
→ 运行完整试运行
→ 自动重新执行发布预检
→ 健康度、步骤导航和发布按钮同步更新
```

全部完成后只显示一个汇总成功提示：

```text
模拟发布验证已全部通过
```

场景卡、完整试运行轨迹和预检面板分别保留内联状态，但不连续弹出多个成功消息。

### 6.2 健康度中文状态

| 错误码 | 中文提示 | 修复定位 |
|---|---|---|
| `TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE` | 还有必测场景未通过：{场景名称} | 第七步场景卡 |
| `TODO_FULL_SIMULATION_REQUIRED` | 完整试运行尚未通过 | “运行完整试运行”按钮 |
| `TODO_FULL_SIMULATION_STALE` | 模板配置已变化，请重新运行完整试运行 | “运行完整试运行”按钮 |

双重门禁通过后：

- 第七步显示“已完成”；
- 配置健康度显示“当前配置可以发布”；
- 发布预检显示 `0 项阻塞`；
- 发布按钮可用。

### 6.3 返回修复

“去修复”必须按问题类型定位：

- 场景不完整：选中第一个未通过场景并滚动到场景卡；
- 完整试运行缺失或失效：滚动并高亮“运行完整试运行”；
- 结构问题：跳转到对应配置步骤和字段。

点击后必须有可见的定位结果，不能只停留在当前页面。

## 7. 版本差异

前端统一使用版本字段规范化函数：

```text
versionId = row.versionId ?? row.version_id ?? row.id
status = row.status ?? row.publishStatus ?? row.publish_status
```

调用差异接口前必须保证左右版本都是正整数：

```text
leftVersionId > 0
rightVersionId > 0
```

如果没有历史发布版本，显示“当前为首个待发布版本”，不调用差异接口。

版本差异属于辅助信息，不是发布门禁。真实接口失败时只在差异区域显示：

```text
版本差异暂时无法加载，发布预检结果不受影响
```

不得再发出包含 `undefined/null/NaN` 的请求，也不得同时弹出全局参数类型错误。

## 8. 错误处理

一次用户操作只产生一个主结果：

- 双重门禁全部通过：成功提示；
- 场景或完整试运行未通过：汇总失败提示；
- 发布预检存在结构问题：显示阻塞数量并提供定位；
- 版本差异失败：仅显示内联非阻塞警告。

技术错误码、请求 ID 和接口路径可以放在可展开的诊断详情中，主界面只显示中文业务含义。

## 9. 测试与验收

### 9.1 后端单元与集成测试

- 三个必要场景通过、完整试运行未运行时，发布仍被阻塞。
- 完整试运行通过、必要场景未齐时，发布仍被阻塞。
- 双重门禁全部通过时，旅程健康度和发布预检均无模拟阻塞。
- 定义哈希变化后，两类旧证据均失效。
- 完整试运行失败只能追加 `FAILED` 证据。
- 只读样例运行前后，业务表和运行时待办表不发生变化。
- 模板详情、工作台和发布预检返回一致状态。

### 9.2 前端契约测试

- `version_id` 能正确解析为发布版本 ID。
- 缺少合法版本 ID 时不调用差异接口。
- 差异失败不触发全局参数错误。
- 完整试运行成功后自动刷新统一准入状态。
- 页面只显示一个最终成功提示。
- 场景、完整试运行和结构问题分别定位到正确区域。

### 9.3 MySQL 与真实浏览器验收

验收路径：

```text
打开 TD-001 首联待办草稿
→ 一键加载只读样例
→ 批量验证有效、疑似无效、未接通
→ 确认实际下一待办分别为 TD-004、TD-002、TD-003
→ 运行完整试运行
→ 确认没有 undefined 差异请求
→ 确认只有一个汇总成功提示
→ 确认配置健康度为 0 项问题
→ 确认第七步为已完成
→ 确认发布预检为 0 项阻塞
→ 刷新页面后再次确认状态一致
```

验收还必须证明：

- 浏览器控制台无异常；
- 前后端请求中不存在 `undefined/null/NaN` 版本参数；
- `todo_instance`、`todo_action_log`、`business_event` 和线索业务表不变；
- `todo_simulation_evidence` 追加三类必要场景证据和一条完整试运行证据；
- 后端测试、前端契约测试、生产构建和真实 MySQL E2E 通过。

## 10. 非目标

- 不修改不可变发布版本。
- 不把模拟结果写入模板定义。
- 不改变 Todo Engine 运行时待办生成规则。
- 不升级 Vue 或 RuoYi 技术栈。
- 不扩展到线索阶段以外的业务模板内容；统一准入能力本身可以被其他模板复用。
