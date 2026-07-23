# Phase 2 Task 13 — 模拟、预检与发布闭环

## 结果

第七步“模拟发布”已从占位页替换为可操作的业务闭环：

- 按当前账号数据权限远程搜索真实业务对象，并提供显式“加载只读样例”入口。
- 由服务器补齐事件载荷并展示覆盖率、字段来源和缺失项；前端只提交 `manualOverrides`。
- 所有敏感字段统一显示为 `••••••`，试运行轨迹不渲染原始载荷或技术明细。
- 试运行前先由服务器编译当前草稿并取得权威 definition hash；只认可该 hash 的成功结果。
- 固定按事件、负责人、DoD、SLA、路由、员工待办预览展示轨迹，并可返回对应步骤修复、重新运行。
- 发布前重新调用服务器预检；阻塞项禁止发布，警告项必须填写复核说明。
- 支持草稿与最近已发布版本的语义差异展示。
- 发布调用不可变版本接口，成功后重新加载旅程并进入已发布只读态。
- 模拟、发布、版本对比按钮分别由精确权限控制。
- 未保存或正在保存的草稿硬阻塞预检、模拟和发布，避免发布旧服务端定义。
- 对象查询与模拟必须同时具备 `todo:simulation:list`、`todo:simulation:simulate`；定义差异仅使用 `todo:definition:diff`。
- 已发布只读态隐藏所有执行控件，版本或状态变化会清除旧模拟/预检证明。
- 默认试运行时间按后端 `LocalDateTime` 契约发送本地无时区秒级值。

## TDD 证据

RED：

```text
npm run test:todo-phase-two
AssertionError: Expected typeof buildHydratedPayloadRows to be function, actual undefined
at scripts/check-todo-journey-model.js:725
```

GREEN：

- Journey model：42 checks
- Phase 2 UX：25 checks
- Todo configuration source contract：通过
- Real-backend E2E source contract：通过
- UTF-8 source gate：通过
- Production build：通过（仅既有 bundle/asset size warnings）

行为覆盖包括：

- hydration 来源与敏感值脱敏；
- 手工覆盖不可变更新；
- 固定轨迹顺序与修复目标；
- 当前草稿 hash 门禁；
- blocker / warning reason 发布门禁；
- simulator / publisher / auditor 权限边界。

## 后端验证

```text
mvn -pl law-todo -am -DskipTests=false test
BUILD SUCCESS
law-file 73 + law-business 28 + law-todo 669 tests
0 failures, 0 errors
```

```text
mvn -DskipTests=false test
BUILD SUCCESS
全部 10 个 reactor module 成功
```

完整 Maven 测试中的跳过项均为需要外部 MySQL/对象存储环境的既有集成测试；本次没有新增后端跳过项。

## 真实 Chrome E2E

新增 `tests/e2e/todo-config-journey.spec.js`，不使用 `page.route`、`route.fulfill` 或伪造 token，覆盖：

- 真实对象 hydration；
- 只读样例且不写入运行态；
- 固定轨迹、修复与重跑；
- DoD / SLA / 路由可解释结果；
- 预检 blocker / warning；
- 不可变发布；
- auditor 按钮边界。

当前执行机没有运行中的 8080 后端、3306 MySQL，且未提供 `TODO_*` 真实 E2E 环境变量，因此没有把 mock 结果冒充为 Chrome 通过。待一次性 E2E 数据库、账号和 `TODO_CONFIG_E2E_JOURNEY_TEMPLATE_ID` 就绪后执行：

```text
npm run test:e2e -- tests/e2e/todo-config-journey.spec.js
```

## 变更范围

- 新增 `SimulationPublishStep.vue`
- 新增 `BusinessObjectPayloadEditor.vue`
- 新增 `SimulationTrace.vue`
- 新增 `PublishPreflightPanel.vue`
- 扩展旅程行为模型、shell 接线与模板版本 diff API
- 扩展 Phase 2 行为/UX 合同与真实后端 E2E source gate

## 独立审查

独立审查首轮提出 4 个 P1：未保存草稿竞态、权限与真实端点不一致、只读/发布角色门禁未闭环、默认时间格式与 `LocalDateTime` 不一致。以上均已修复，并重新执行行为测试、E2E source gate、UTF-8 gate 和生产构建。

未修改、未暂存既有本地工作：

- `.superpowers/sdd/task-7-report.md`
- `ruoyi-ui/vue.config.js`
- `.playwright-cli/`
- `.runtime-logs/`
- `output/`

## 确定性 E2E 加固（独立后续提交）

针对最终审查提出的“场景可能被条件分支跳过”问题，本轮将旅程 E2E 改为固定数据、固定账号、固定断言：

- 引导 SQL 固定创建 `REPAIR`、`FAILED`、`WARNING` 三个草稿，不再依赖 `TODO_CONFIG_E2E_JOURNEY_TEMPLATE_ID`。
- 固定创建 `todo_business_admin`、`todo_resource_admin`、`todo_publisher`、`todo_auditor` 四个最小权限账号，并分别验证可见性与后端写权限边界。
- Schema 修复必须真实打开资源维护抽屉、创建可编辑版本、补充字段、保存、返回旅程并重新模拟。
- 失败场景使用不存在的工作日历确定性地产生模拟阻塞，并验证发布按钮保持禁用。
- 告警场景绑定显式的未决非阻塞决策；编译器把它投影为 `TODO_DECISION_REVIEW_REQUIRED`，从而复用既有“告警必须填写发布说明”门禁。普通模板和普通空条件触发器不受影响。
- 样例模拟前后直接读取一次性 E2E 数据库，逐项比较 `todo_instance`、`todo_relation`、负数样例业务行以及真实测试线索指纹，证明模拟没有写入运行态或业务对象。
- 旅程测试只保留一个“真实环境不存在”级别的 `test.skip`；场景内部不再有可选修复、可选告警或可选角色分支，也未使用 `page.route`、`route.fulfill` 或伪造 token。
- 清理器扩展为校验并删除本次运行的三个模板、事件新版本、advisory decision、五个测试账号/角色及其授权；检测到运行态待办或关系时拒绝级联清理。

TDD 与回归证据：

```text
RED: npm run test:e2e:contract
     Todo journey deterministic E2E scenarios must not include if (await

GREEN: npm run test:e2e:contract
       Todo configuration real-backend E2E source contract passed
       External-database Surefire report gate negative contract passed
```

```text
RED: TodoDefinitionCompilerTest.unresolvedNonBlockingDecisionProducesAReviewWarning
     expected true but was false

GREEN: mvn -pl law-todo test
       Tests run: 670, Failures: 0, Errors: 0, Skipped: 2
```

同时通过：

- `node --check`：旅程 E2E、数据库 helper、源码契约脚本；
- `npm run test:todo-phase-two`：42 个模型检查、25 个 UX 检查；
- `npm run test:todo-config`；
- `npm run test:encoding`。

真实运行仍留给 Task14：本机检查结果为 `8080=STOPPED`、`3306=STOPPED`、`TODO_ENV=ABSENT`。因此本轮没有把源码契约或 mock 冒充为 Chrome 真实通过；Task14 启动一次性数据库和后端后，应直接执行：

```text
npm run test:e2e -- tests/e2e/todo-config-journey.spec.js
```

## 最终 E2E 审查修复：精确版本与真实失败重跑

最终审查发现上一轮两个场景的断言虽然严格，但业务路径不成立，本节结论取代上一节中对应场景的描述：

1. 原 Schema 场景从启用中的 `eventType@v1` 创建并保存了 `v2`，却没有启用 `v2`，模板也仍绑定 `v1`。刷新后的字段目录可能造成页面看似健康，但实际编译和模拟仍指向不完整版本。
2. 原失败场景通过不存在的日历制造错误；该错误会在发布预检阶段提前返回，因此模拟引擎不会运行，页面也不会产生六段模拟轨迹。

修复后的确定性路径：

- Schema 场景先从数据库读取模板实际绑定的事件类型、Payload 版本、Schema 状态和资源状态，并确认初始版本为 `ACTIVE / INCOMPLETE`。
- 用户在真实资源抽屉中创建下一版本，补充整数型 `ownerId` 字段、生成样例并保存；测试等待真实创建版本接口和版本元数据显示完成，避免抽屉异步加载竞态。
- 保存后的新版本必须由数据库确认是 `DRAFT / READY`，随后通过已认证的真实状态接口将该精确资源 ID 启用。
- 回到旅程后，用户选择新启用的 Payload 版本并保存模板。数据库使用 `definition_json.event.eventType + payloadVersion` 精确连接资源目录，断言模板已绑定新的 `ACTIVE / READY` 版本且字段数大于零；页面同时断言版本号、健康状态和 `ownerId`。
- 最终使用事件资源自身的只读样例对象运行六段模拟，并确认不存在阻塞轨迹。
- 失败模拟 Fixture 改为 Schema 合法、日历合法的规范化条件 `ownerId == -999999999`。发布预检通过后，真实业务对象会得到 `NOT_MATCHED` 模拟结果、完整六段轨迹和禁用的发布按钮。
- `SimulationTrace` 将 `NOT_MATCHED` 明确显示为阻塞状态。用户返回触发条件步骤删除该条件并保存，再次选择同一真实业务对象运行模拟；第二次六段轨迹无阻塞，发布按钮恢复可用。

源码契约 TDD：

```text
RED: npm run test:e2e:contract
     Todo journey real E2E spec must include loadJourneyEventBinding

GREEN: npm run test:e2e:contract
       Todo configuration real-backend E2E source contract passed
       External-database Surefire report gate negative contract passed
```

本轮额外验证：

- 三个相关 JavaScript 文件通过 `node --check`；
- Playwright `--list` 识别且仅识别 7 个固定旅程场景；
- `npm run test:todo-phase-two`：42 个模型检查、25 个 UX 检查通过；
- `npm run test:todo-config`、`npm run test:encoding` 通过；
- `npm run build:prod` 成功，只有仓库既有的资源体积警告。

真实浏览器执行条件仍未出现：本机 `8080`、`3306` 未监听，且不存在 `TODO_*` 环境变量。本轮没有以源码契约替代真实运行结论；Task14 仍需在一次性 E2E 数据库和后端启动后执行完整 Playwright 旅程。
