# 线索待办流程运行验证检查点

更新时间：2026-07-26
分支：`runtime/startup-wiring-fix`

## 结论

本检查点已经具备由项目负责人重启最新前后端并进行页面确认的条件，但**不等于 16 个线索待办场景已经完成最终全量验收**。

- 场景 01“标签确认、轮询分配”在真实 Chrome、真实 API、独立 MySQL 中连续 3 次通过。
- 场景 06、10、12 已完成针对性真实浏览器验证，分别覆盖自动无效复核、SLA 阈值和越权/幂等。
- 其余场景已经进入同一份零跳过 Playwright 规格，但尚未取得一次完整 16/16 全绿的最终报告。
- GitHub Actions 已增加 `lead-todo-real-e2e` 作业；本检查点只完成本地契约校验，尚未以远程 Actions 结果宣告通过。

因此本提交的定位是“页面确认 checkpoint”，后续仍需在最新服务重启后继续完整 16 场景验收。

## 本检查点完成内容

### 前端

- 标签确认按钮直接依赖 Vuex `permissions`，支持 `lead:tag:confirm` 和 `*:*:*`。
- 标签确认按钮不再使用会直接删除 DOM 的 `v-hasPermi`。
- 操作容器和标签确认容器使用“权限集合 + 线索编号”稳定 key，保证 Element UI 主表与 fixed 操作列同步重渲染。
- 增加延迟权限契约：权限为空时不显示，异步授予后主表/fixed 两份均显示，移除后消失，销售权限不显示，通配权限显示。
- 保留 `TodoDynamicForm` 单一 `v-bind="componentAttrs(field)"` 实现及其三项契约，避免动态字段属性重复绑定。
- 重试队列、无效复核、Dead-Pool 页面增加真实流程所需的稳定测试定位点。

### 后端与数据库

- 修复自动无效复核保留原责任复核人，系统操作人保持 `0`，避免数据范围内无法看到自动复核结果。
- 增加受治理的轮询分配游标和候选人可用性处理。
- 补齐线索读取模型、重试时间、Dead-Pool/复核查询和事件 payload。
- 补齐销售、主管、信息员最小运行权限；生产迁移不创建测试用户。
- 测试身份仍由 profile + secret 显式启用的 seeder 创建和绑定。
- E2E 文件清理通过真实 `FileObjectService` 退役接口执行，不直接删除文件元数据。
- E2E SLA 扫描频率覆盖只在测试 profile 和测试身份开关同时启用时生效。
- Todo 完成生命周期提供显式 Spring wiring，默认 No-Op 实现不会覆盖真实业务实现。

### 自动化与清理

- 真实 E2E 使用独立数据库身份校验、不可伪造运行能力和精确 ownership manifest。
- 清理范围覆盖本轮创建的线索、事件、待办、动作、通知、复核、游标、可用性、标签、部门标记和文件。
- 对重叠运行标记、部分初始化失败、后端/数据库身份不匹配和密钥脱敏均有 Node 契约。
- 场景 01 三次运行均在 `afterAll` 后通过零残留审计。

## 已执行门禁

| 门禁 | 结果 |
|---|---|
| 场景 01 真实 Chrome 连续 3 次 | 3/3 通过 |
| `npm run test:lead-e2e-helpers` | 11/11 通过，0 skip |
| `npm run test:todo` | 通过，包含动态表单 3 项契约 |
| `npm run test:foundation-ci` | 通过 |
| `npm run test:encoding` | 通过 |
| `npm run test:e2e:server` | 通过 |
| `npm run test:foundation-identities` | 通过 |
| `npm run build:prod` | 通过；保留 4 个既有 CSS/体积警告 |
| `mvn test` | 全 Reactor `BUILD SUCCESS` |

Maven 默认测试中按既有设计存在需要外部 MySQL/profile 才运行的条件跳过；本检查点的真实 Chrome 场景使用独立 MySQL，未跳过目标场景。

## 尚未完成的最终验收

1. 在本检查点提交对应的最新前后端服务重启后，执行完整 16 场景单次串行验收。
2. 要求 Playwright JSON 结果 `unexpected=0`、`skipped=0`，并核对 16 个预期场景全部执行。
3. 再次执行数据库与文件零残留审计。
4. 观察远程 `lead-todo-real-e2e` GitHub Actions 作业并处理环境差异。
5. 完成后更新本报告为最终验收结论；在此之前不得将当前检查点描述为 16/16 全量完成。
