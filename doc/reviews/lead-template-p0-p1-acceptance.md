# TD-001 线索待办模板 P0/P1 验收报告

验收日期：2026-07-30

验收分支：`runtime/startup-wiring-fix`
验收范围：`doc/reviews/lead-template-p0-p1-test-matrix.md` 定义的全部 P0 工作包与 P1 降低配置复杂度项目。

## 结论

TD-001“首联待办”模板的 P0、P1 项目全部通过，具备进入后续线索待办流程联调的条件。

- P0：10/10 通过。
- P1：7/7 通过。
- 后端全仓：1,348 个测试，0 失败，0 错误；其中 25 个依赖外部环境的测试按设计跳过，相关 MySQL 真实集成测试已另行执行。
- 全新 MySQL 8 数据库：从 v0.15 基线完整迁移至 v0.20.69。
- 前端：51 个旅程模型检查、30 个 UX 检查及全部配置中心、Schema、编码、E2E 源码合同检查通过。
- Chrome + 真实后端：3 个端到端用例通过，覆盖七步页面、三条受控场景、发布就绪状态和控制台零错误。
- 生产构建：通过；保留既有 CSS 顺序和包体积警告，不影响本次功能验收。

## 用户问题与根因闭环

| 用户问题 | 根因 | 修复结果 |
|---|---|---|
| 事件字段只显示“业务字段”，无法理解和选择 | 事件资源未按事件版本收敛，字段缺少中文标题、说明和语义角色 | `LEAD_ASSIGNED@1` 字段按版本治理，显示中文名称、来源、说明；负责人白名单仅保留 `ownerId` |
| `分配记录ID 不等于 undefined`，预检报类型错误 | 空值运算被当成普通比较运算，摘要与服务端类型校验不一致 | 空值判断统一为 `NOT_EMPTY`/`IS_EMPTY`；无值运算不再渲染比较值，类型校验与摘要一致 |
| 负责人下拉含义不清，切换策略会丢失选择 | 候选字段未按负责人资格过滤；不同策略共用同一个临时值 | 事件、角色、人员、候选池分别保留独立草稿，只有“应用此规则”才提交；人员/角色显示业务名称而非 ID |
| 后续路由要求手填“业务结果” | 路由只支持自由文本，未使用首联结果字典 | 首联结果由受控字典下发，一键生成 `VALID→TD-004`、`SUSPECT_INVALID→TD-002`、`UNREACHABLE→TD-003` |
| 样例字段乱码或只有英文缩写 | 场景、字段和字典值缺少语义映射，旧菜单/系统数据存在可逆乱码 | 页面展示中文场景名、字段名及目录名称；源码编码检查与浏览器可见文本检查通过 |
| 三条场景始终未完成，无法试运行 | TD-001 草稿引用旧版 TD-003；路由目录返回历史版本且覆盖当前版本；同输入证据唯一键阻止失败后重跑 | 跨模板引用原子更新到当前已发布版本；目录仅返回当前版本且防御性保留最新项；模拟证据改为追加式不可变记录 |
| 切换到后续路由时控制台报 `$options` 空引用 | 高级路由编辑器的 `el-form-item` 脱离 `el-form` 容器 | 补齐表单容器，七步切换控制台零错误 |

## P0 验收

| 编号 | 状态 | 验收证据 |
|---|---|---|
| P0-01 事件字段中文语义 | 通过 | 资源目录单测、MySQL TD-001 集成测试、Chrome 第一步截图 |
| P0-02 负责人字段白名单 | 通过 | 资源目录测试、负责人策略测试、MySQL 字段断言 |
| P0-03 条件类型与空值语义 | 通过 | 类型检查、旅程评估器、前端模型检查 |
| P0-04 负责人草稿保留 | 通过 | 前端模型与 UX 合同检查 |
| P0-05 受控业务结果路由 | 通过 | 结果目录、路由物化、MySQL 草稿断言 |
| P0-06 三条场景真实运行 | 通过 | 外部 MySQL 集成测试、真实 Chrome API 场景测试 |
| P0-07 模拟不写运行时数据 | 通过 | 外部 MySQL 运行表指纹断言 |
| P0-08 发布预检解除阻塞 | 通过 | 三条当前哈希证据、批量结果 `publicationReady=true` |
| P0-09 不改写发布历史 | 通过 | 迁移前后发布版本、触发入口及历史证据指纹断言 |
| P0-10 规范 JSON 与哈希 | 通过 | Java 规范化哈希、`definition_json=compiled_json` 断言 |

## P1 验收

| 编号 | 状态 | 验收证据 |
|---|---|---|
| P1-01 事件字段按版本收敛 | 通过 | 事件目录与浏览器第一步 |
| P1-02 负责人来源可解释 | 通过 | 负责人目录、第三步页面及草稿恢复测试 |
| P1-03 路由结果免手写 | 通过 | 推荐路由物化、后端目录与第六步页面 |
| P1-04 样例中文语义化 | 通过 | 场景目录、前端 UX 检查及第七步页面 |
| P1-05 中文修复动作定位 | 通过 | 旅程评估器及问题本地化模型测试 |
| P1-06 跨步骤影响提示 | 通过 | 依赖影响服务与前端权威健康状态检查 |
| P1-07 七步导航可用 | 通过 | 1,672×941 Chrome 七步截图、可见文本与控制台检查 |

## 新增迁移与兼容边界

- `V0_20_67__PublishCanonicalTd003SelfReference`：为 TD-003 发布内部自洽的新不可变版本，不覆盖既有发布历史。
- `V0_20_68__RepointTd001DraftToCurrentTargets`：只更新带 V65 修复标记的 TD-001 可编辑草稿，同时更新业务结果和高级路由节点引用。
- `V0_20_69__append_only_simulation_evidence.sql`：移除“同输入只允许一条证据”的唯一约束，模拟重跑追加新证据，旧证据保持不可变。
- 路由目标目录只返回模板当前已发布版本；服务层对历史重复项使用“首次有效值优先”防御。

## 执行命令与结果

### 后端全仓

```text
mvn --batch-mode --no-transfer-progress clean verify
```

结果：`BUILD SUCCESS`；1,348 个测试，0 失败，0 错误，25 个按环境条件跳过。

### 全新数据库迁移

基线脚本按 CI 顺序导入全新数据库 `lead_template_p0p1_full_v69_e2e`，随后执行：

```text
mvn --batch-mode --no-transfer-progress -pl ruoyi-admin -am
  -Dtest=FlywayMigrationTest
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：从 v0.15.0 完整迁移至 v0.20.69，测试通过。

### 真实 MySQL 业务验收

```text
LeadTemplateConfigurationMySqlIT
TodoScenarioSimulationExternalMysqlIT
```

结果：TD-001 当前草稿、三条结果路由、不可变边界、运行时零写入和追加式模拟证据全部通过。

### 前端合同与生产构建

```text
npm run test:todo
npm run test:todo-config
npm run test:todo-phase-two
npm run test:todo-schema
npm run test:encoding
npm run test:e2e:contract
npm run build:prod
```

结果：全部通过；旅程模型 51 项、UX 30 项。

### Chrome + 真实后端

```text
npx playwright test tests/e2e/todo-config-center.spec.js --project=chromium
```

结果：3/3 通过。

1. 旧配置中心路由可用且不修改发布版本。
2. TD-001 三条场景实际下一待办全部正确，批量门禁允许发布。
3. 七步页面逐步可见、中文语义完整、不出现 `undefined` 或替换字符、控制台零错误。

## 浏览器截图

- `output/playwright/lead-template-p0-p1/step-01-event.png`
- `output/playwright/lead-template-p0-p1/step-02-trigger.png`
- `output/playwright/lead-template-p0-p1/step-03-owner.png`
- `output/playwright/lead-template-p0-p1/step-04-dod.png`
- `output/playwright/lead-template-p0-p1/step-05-sla.png`
- `output/playwright/lead-template-p0-p1/step-06-routing.png`
- `output/playwright/lead-template-p0-p1/step-07-simulation-publish.png`

截图为本地验收产物，不纳入产品源码提交。

## 后续准入建议

可以进入线索待办流程的下一轮业务联调，但仍应保持以下发布门槛：

1. 每次修改事件字段、负责人、完成标准或路由后，重新运行三条受控场景。
2. 任何跨模板引用只允许指向目标模板的当前已发布版本。
3. 模拟证据只追加，不覆盖；发布门禁只认可当前定义哈希的通过证据。
4. 线索业务状态和待办状态继续通过 Outbox 事件衔接，禁止前端直接推动待办运行时状态。
