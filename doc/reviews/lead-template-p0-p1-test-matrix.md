# TD-001 线索待办模板 P0/P1 测试矩阵

## 范围与判定原则

本矩阵覆盖“首联待办（TD-001）配置易用性加固”计划。数据库、服务、前端模型和真实浏览器证据缺一不可；只验证页面文案或只验证 SQL 均不能作为最终通过依据。

不可变发布版本、现有触发入口和历史模拟证据必须保持不变。配置修复只允许生成带固定标记的可编辑草稿。

## P0 功能与证据

| 编号 | 用户问题 / 验收要求 | 自动化证据 | 核心断言 |
|---|---|---|---|
| P0-01 | 事件字段必须说明业务含义，不能显示“业务字段” | `TodoConfigurationResourceCatalogServiceTest`、`TodoPayloadSchemaDescriptorTest`、`LeadTemplateConfigurationMySqlIT.assertEventSemantics` | `LEAD_ASSIGNED@1` 的每个字段有中文标题和非空说明 |
| P0-02 | 只有合法负责人字段可选 | `TodoConfigurationResourceCatalogServiceTest`、`LeadTemplateConfigurationMySqlIT.assertEventSemantics` | 负责人白名单恰好为 `ownerId`，`operatorId` 不可作为负责人 |
| P0-03 | 条件运算符和值必须类型匹配，不能出现 `undefined` | `ConditionTypeCheckerTest`、`TodoConfigurationJourneyEvaluatorTest`、`check-todo-journey-model.js` | `NE` 缺值被阻止；`NOT_EMPTY` 无需值；中文摘要不含 `undefined` |
| P0-04 | 切换负责人策略时不能丢失已填草稿 | `check-todo-journey-model.js`、`check-todo-phase-two-ux.js` | 各策略独立保存草稿，切回时恢复；保存只提交当前策略 |
| P0-05 | 业务结果必须从受控选项选择并生成确定路由 | `TodoBusinessOutcomeCatalogServiceTest`、`TodoJourneyBusinessOutcomeMaterializerTest`、`LeadTemplateConfigurationMySqlIT.assertDraft` | `VALID→TD-004`、`SUSPECT_INVALID→TD-002`、`UNREACHABLE→TD-003` |
| P0-06 | 三条必测场景可实际运行 | `TodoSimulationScenarioServiceTest`、`TodoScenarioSimulationExternalMysqlIT`、`todo-config-center.spec.js` | 三条场景的实际下一待办等于预期且均通过 |
| P0-07 | 模拟不得污染运行数据 | `TodoScenarioSimulationExternalMysqlIT.threeGovernedScenariosResolveExpectedTargetsWithoutRuntimeWrites` | 运行表行数与哈希不变；仅模拟证据允许新增 |
| P0-08 | 发布预检不得继续报类型错误或“场景未完成” | `TodoConfigurationJourneyEvaluatorTest`、`TodoSimulationEvidenceServiceTest`、真实浏览器终态 | 当前定义哈希对应三条证据，阻塞场景为空 |
| P0-09 | 修复不能改写发布历史 | `LeadTemplateConfigurationHardeningMigrationTest`、`LeadTemplateConfigurationMySqlIT` | 发布行、触发入口和历史证据迁移前后指纹完全一致 |
| P0-10 | 修复草稿必须采用引擎规范 JSON 与哈希 | `LeadTemplateConfigurationMySqlIT.assertDraft`、`V0_20_66__CanonicalizeTd001RepairDraft` | `definition_json=compiled_json`，Java 规范化哈希与存储值一致 |

## P1 降低配置复杂度与证据

| 编号 | 优化要求 | 自动化证据 | 核心断言 |
|---|---|---|---|
| P1-01 | 字段按当前事件版本收敛并展示语义角色 | `TodoConfigurationResourceCatalogServiceTest`、前端资源合同 | 不混入其他事件同名占位字段，显示来源、角色和说明 |
| P1-02 | 负责人下拉只展示可用来源并解释来源 | `TodoOwnerStrategyServiceTest`、`check-todo-phase-two-ux.js` | 不合格字段不可选；用户、部门、角色显示名称而非 ID |
| P1-03 | 路由结果预置，不要求手写业务结果 | `TodoBusinessOutcomeCatalogServiceTest`、`check-todo-journey-model.js` | 结果字典与推荐目标由后端下发，一键生成三条路由 |
| P1-04 | 一键加载只读样例并显示中文语义值 | `TodoSimulationScenarioCatalogTest`、`check-todo-phase-two-ux.js` | 只展示当前模板所需字段；代码/ID 有中文或目录名称 |
| P1-05 | 校验问题能定位到具体步骤并给出中文修复动作 | `TodoConfigurationJourneyEvaluatorTest`、`check-todo-journey-model.js` | 问题归属触发、负责人、路由或模拟发布步骤，不退化为英文原文 |
| P1-06 | 跨步骤变更明确提示影响并使用权威健康状态 | `TodoJourneyDependencyServiceTest`、`check-todo-phase-two-ux.js` | 变更事件/负责人/路由时列出受影响步骤；页面不自行猜测可发布状态 |
| P1-07 | 七步导航在常见桌面宽度下可用 | `check-todo-phase-two-ux.js`、真实 Chrome 截图 | 步骤可见、可返回修复、无横向遮挡，当前步骤和阻塞数明确 |

## 执行层级

1. Java 单元/服务测试验证类型、资源、路由和证据规则。
2. MySQL 8 集成测试验证真实 Flyway 迁移、规范哈希、不可变边界和三路模拟。
3. Node 前端合同测试验证七步配置模型、中文语义与草稿保留。
4. 生产构建验证 Vue 模板与依赖集成。
5. Chrome + 真实后端浏览器测试验证操作路径、API 响应、截图和控制台。

最终验收结果与命令、版本、截图清单记录在 `lead-template-p0-p1-acceptance.md`；未产生新鲜证据的项目不得标记为通过。
