# Todo Engine Phase One Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `V0.16` 完整实现可复用 Todo Engine 阶段一，并打通线索分配到首联的端到端链路。

**Architecture:** 新增 `law-todo` 模块作为模块化单体中的独立领域模块，复用现有 Outbox、RuoYi 权限、Quartz、文件和通知能力。业务模块通过事件和处理器接口接入，Todo Engine 不直接修改业务表。

**Tech Stack:** Java 17、Spring Boot、MyBatis、MySQL 8、Flyway、Quartz、JUnit 5、Mockito、Vue 2、Element UI。

## Global Constraints

- 分支必须从远程 `v0.15` 提交 `f3214c1c6349539562d68c9d7f9bb79339c35d05` 创建，名称为 `V0.16`。
- 不引入微服务、MQ 或新的前端框架。
- 所有生产行为遵循测试先行，写接口使用 DTO 和 `actionId` 幂等键。
- Todo 不直接更新线索、合同或案件状态。
- 每个任务完成后运行模块测试并形成独立提交。

---

### Task 1: 模块骨架与领域状态机

**Files:**
- Create: `law-todo/pom.xml`
- Create: `law-todo/src/main/java/com/law/todo/domain/TodoStatus.java`
- Create: `law-todo/src/main/java/com/law/todo/domain/TodoStatusTransitions.java`
- Test: `law-todo/src/test/java/com/law/todo/domain/TodoStatusTransitionsTest.java`
- Modify: `pom.xml`

**Interfaces:**
- Produces: `TodoStatus.fromCode(String)`、`TodoStatusTransitions.requireAllowed(TodoStatus, TodoStatus)`。

- [ ] 写测试，断言主路径、退回路径和非法终态转换。
- [ ] 运行 `mvn -pl law-todo -am test`，确认因模块/类型不存在而失败。
- [ ] 创建模块、状态枚举和转换策略，并加入父 POM。
- [ ] 重跑测试，预期全部通过。
- [ ] 提交 `feat(todo): establish todo domain module`。

### Task 2: 数据库迁移与持久化模型

**Files:**
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_16_1__todo_engine.sql`
- Create: `law-todo/src/main/java/com/law/todo/domain/model/*.java`
- Create: `law-todo/src/main/java/com/law/todo/mapper/TodoMapper.java`
- Create: `law-todo/src/main/resources/mapper/todo/TodoMapper.xml`
- Test: `law-todo/src/test/java/com/law/todo/mapper/TodoPersistenceContractTest.java`

**Interfaces:**
- Produces: `insertInstance`、`selectById`、`updateStatusConditionally`、`insertActionIfAbsent`、`insertTriggerIfAbsent`。

- [ ] 写迁移契约测试，读取 SQL 并断言实例、动作、触发幂等唯一键和工作队列索引存在。
- [ ] 运行测试并确认失败。
- [ ] 创建 11 张核心表、实体、Mapper 接口和 XML。
- [ ] 运行模块测试并校验 MyBatis XML 可解析。
- [ ] 提交 `feat(todo): add todo persistence model`。

### Task 3: 待办生命周期与幂等动作

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoCommandService.java`
- Create: `law-todo/src/main/java/com/law/todo/application/command/TodoActionCommands.java`
- Create: `law-todo/src/main/java/com/law/todo/domain/TodoAccessPolicy.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoCommandServiceTest.java`

**Interfaces:**
- Produces: `claim`、`start`、`submit`、`complete`、`returnTodo`、`transfer`、`cancel`。

- [ ] 分别写动作状态转换、重复 `actionId`、越权、并发更新失败测试并确认失败。
- [ ] 实现命令 DTO、访问策略、条件更新和动作日志事务。
- [ ] 验证重复动作返回原结果且不新增日志。
- [ ] 运行 `mvn -pl law-todo -am test`。
- [ ] 提交 `feat(todo): implement idempotent lifecycle actions`。

### Task 4: 模板、DoD 与下一待办

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoTemplateService.java`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoDodService.java`
- Create: `law-todo/src/main/java/com/law/todo/spi/TodoBusinessValidator.java`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoRoutingService.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoTemplateAndRoutingTest.java`

**Interfaces:**
- Produces: `publishTemplate`、`validateDod`、`createNextTodos`。

- [ ] 写不可变模板版本、缺少字段/附件、业务校验失败和下一节点幂等测试并确认失败。
- [ ] 实现版本发布、DoD 快照校验、处理器注册和下一节点路由。
- [ ] 运行模块测试并确认历史实例仍引用原版本。
- [ ] 提交 `feat(todo): add templates dod and routing`。

### Task 5: Outbox 触发与候选人解析

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/integration/TodoBusinessEventHandler.java`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoAssignmentResolver.java`
- Test: `law-todo/src/test/java/com/law/todo/integration/TodoBusinessEventHandlerTest.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/BusinessEventOutboxProcessor.java`

**Interfaces:**
- Consumes: `BusinessEventHandler.handle(BusinessEventRecord)`。
- Produces: 用户、角色、部门、岗位候选解析和事件幂等创建。

- [ ] 写重复事件、无匹配规则、Owner 解析和候选池测试并确认失败。
- [ ] 实现事件处理器、触发规则查询和候选解析。
- [ ] 验证重复事件不创建第二张待办。
- [ ] 提交 `feat(todo): consume business events idempotently`。

### Task 6: SLA、工作日历与升级

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoSlaService.java`
- Create: `law-todo/src/main/java/com/law/todo/domain/service/WorkingTimeCalculator.java`
- Create: `law-todo/src/main/java/com/law/todo/job/TodoSlaTask.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoSlaServiceTest.java`

**Interfaces:**
- Produces: `calculateDueAt`、`pause`、`resume`、`scanAndEscalate`。

- [ ] 写跨周末、例外工作日、暂停恢复和 80/100/150% 单次触发测试并确认失败。
- [ ] 实现工作时间计算、SLA 记录和升级动作。
- [ ] 注册 Quartz Bean 调用入口并写任务测试。
- [ ] 提交 `feat(todo): implement working calendar and sla escalation`。

### Task 7: 权限、查询与 REST API

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoQueryService.java`
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoController.java`
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoTemplateController.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/todo/TodoControllerTest.java`

**Interfaces:**
- Produces: dashboard、list、detail 和全部生命周期 REST 接口。

- [ ] 写参数校验、权限码、本人/候选池/下属查询和业务错误码 API 测试并确认失败。
- [ ] 实现查询服务、权限常量和 Controller。
- [ ] 增加菜单、字典和角色权限迁移 `V0_16_2__todo_permissions.sql`。
- [ ] 提交 `feat(todo): expose secured todo api`。

### Task 8: 前端待办中心与公共组件

**Files:**
- Create: `ruoyi-ui/src/api/todo.js`
- Create: `ruoyi-ui/src/views/todo/index.vue`
- Create: `ruoyi-ui/src/views/todo/components/TodoDetailDrawer.vue`
- Create: `ruoyi-ui/src/views/todo/components/TodoActionDialogs.vue`
- Create: `ruoyi-ui/src/views/todo/components/TodoSummaryCard.vue`
- Create: `ruoyi-ui/src/views/todo/components/TodoRelationPanel.vue`

**Interfaces:**
- Consumes: Task 7 REST API。
- Produces: 待办中心、工作台摘要和业务内嵌面板。

- [ ] 先创建 API 契约单元测试或静态导出检查，确认组件/函数不存在。
- [ ] 实现本人、候选池、超时列表和详情动作。
- [ ] 接入文件上传、字典、权限指令、加载态和错误反馈。
- [ ] 运行 `npm run build:prod`。
- [ ] 提交 `feat(todo): add todo center frontend`。

### Task 9: 线索分配到首联端到端链路

**Files:**
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizLeadServiceImpl.java`
- Create: `law-todo/src/main/java/com/law/todo/integration/lead/LeadFirstContactHandler.java`
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_16_3__lead_first_contact_template.sql`
- Test: `ruoyi-system/src/test/java/com/ruoyi/system/integration/LeadTodoFlowTest.java`

**Interfaces:**
- Consumes: `LEAD_ASSIGNED` 事件。
- Produces: `LEAD_FIRST_CONTACT` 待办；完成处理器调用现有线索跟进 Service。

- [ ] 写分配事件生成一张首联待办、重复事件幂等、DoD 缺失拒绝、完成写跟进记录测试并确认失败。
- [ ] 实现模板种子、事件映射和完成处理器。
- [ ] 验证 Todo 失败不回滚已提交业务事务，Outbox 可重试恢复。
- [ ] 提交 `feat(todo): connect lead first contact flow`。

### Task 10: 全量验证与阶段一准入

**Files:**
- Create: `doc/v0.16-todo-phase-one-acceptance.md`
- Modify: `.github/workflows/v0.15-quality-gate.yml`

**Interfaces:**
- Produces: 可重复执行的阶段一验收证据。

- [ ] 运行 `mvn -DskipTests=false package`，预期全部模块成功且零失败。
- [ ] 从空 MySQL 执行全部 Flyway 迁移，预期版本到 `V0_16_3`。
- [ ] 运行 `npm install && npm run build:prod`，预期生产构建成功。
- [ ] 执行首联链路集成测试及重复事件、并发动作、SLA 扫描测试。
- [ ] 推送 `V0.16`，确认 GitHub Actions 后端、前端和迁移任务全部成功。
- [ ] 按设计验收标准逐项记录证据后提交 `docs: record v0.16 phase one acceptance`。
