# 缴费、转案与案管接收基础治理设计

## 1. 背景与目标

v0.2 最终要以 Todo Engine 驱动“线索接入 → 分配 → 首联 → 跟进 → 合同 → 审批 → 签署 → 缴费 → 转案 → 案管接收”的销售主链路。本子项目延续前两段基础治理，只治理现有业务事实层，不实现新的 v0.2 业务能力。

本设计中的“转案”必须区分两个已有含义：

1. **商务交案**：合同签署、缴费/开票处理后，由现有 `CASE_CREATE_CHECK` 完成案件生成检查，形成 `pending` 待分案案件。
2. **律师转案**：办理中案件由当前主办律师发起转案，经审批后由拟转入律师确认接案。

本子项目同时治理商务交案、初次分案、律师转案和接案确认，使 Todo Engine 能调用稳定、强类型、可幂等、可回滚的业务入口。

## 2. 范围

### 2.1 本次完成

- 合同到案件生成的前置条件、并发幂等和稳定事件事实。
- 单次分案、批量分案、转案申请、转案审批、接案确认的强类型命令链路。
- 案件对象访问策略和指定处理人校验。
- 案件状态转换、条件更新、业务记录、状态日志与 Outbox 的事务一致性。
- `CASE_CREATED`、`CASE_ASSIGNED`、`CASE_TRANSFER_REQUESTED`、`CASE_TRANSFER_APPROVED`、`CASE_ACCEPTED`、`CASE_REJECTED` 的稳定幂等键。
- 律师档案写接口的强类型化，避免案件模块继续保留 Controller 写入 `Map`。
- 修复案件中心页面的非法 UTF-8 数据并保留既有交互。
- 后端单元测试、跨模块事务测试、结构守卫、源码编码检查和真实案件页面 E2E。

### 2.2 明确不做

- 不新增 PRD 的 11 项转案材料。
- 不新增二次冲突审查、案管分类、业务线或非诉标签。
- 不新增 `biz_case_transfer_material`、`biz_case_classification_log` 或相关字段。
- 不新增字典、Todo 模板、触发规则、事件类型或数据库迁移。
- 不改变现有菜单、权限编码、URL、前端 JSON 字段或案件状态编码。
- 不开发 v0.2 的 SLA、DoD、异常流程或新页面。

## 3. 现状结论

现有代码已经具备案件生成、分案、转案、接案确认和 Todo 处理器，但不能直接作为后续 v0.2 的稳定业务底座：

- Controller 接收 DTO 后立即转成 `Map<String,Object>`，Service 和接口仍暴露弱类型写操作。
- 案件生成、分案、转案和接案事件的幂等键包含随机 UUID，重复动作不能依赖业务事实去重。
- `CaseQueryService.requireAccess()` 只解决读取范围，未表达“谁可以发起转案”和“谁可以确认接案”。
- 接案确认未校验当前操作人是否为 `confirm_user_id`。
- 转案申请只校验可读和状态，未校验申请人与当前案件关系。
- `CaseStatusTransitions` 已存在，但转案和确认服务未统一使用。
- `updateCaseConfirmResult` 接受 `confirming` 或 `processing`，并发条件过宽。
- 现有测试主要验证参数映射和少量异常，未证明业务记录、状态日志、案件状态和 Outbox 同事务回滚。
- `ruoyi-ui/src/views/case/index.vue` 当前不是合法 UTF-8，包含 9,163 个替换字符；现有前端生产构建没有阻止该损坏进入分支。

## 4. 方案选择

### 4.1 采用：定向基础治理

保持模块化单体和现有表结构，沿用 `law-business` 契约模块、`ruoyi-system` 应用服务和事务型 Outbox。把每个业务动作收敛为强类型命令、对象访问策略、条件状态更新、业务事实记录和稳定事件。

该方案能直接服务后续 v0.2，又不会提前引入尚未冻结的转案材料和案管分类口径。

### 4.2 不采用：最小补丁

只替换随机事件键和补权限判断，仍会保留 DTO → Map → Service 的弱类型边界，也无法建立可审计的跨模块事务测试。

### 4.3 不采用：提前开发完整 PRD 转案模块

同时建设 11 项材料、二次冲突和案管分类会突破“暂不开发 v0.2 新业务”的目标，并把基础治理与业务口径实现混在同一交付中。

## 5. 目标架构

```text
Controller / Todo Completion Handler
             │
             ▼
     strong-typed command
             │
             ▼
CaseCreation / Assignment / Transfer / Confirmation Service
             │
      ┌──────┼─────────┐
      ▼      ▼         ▼
AccessPolicy State  Dictionary/User validation
      │      │         │
      └──────┼─────────┘
             ▼
conditional business update
             │
      ┌──────┼─────────┐
      ▼      ▼         ▼
business record  status log  Outbox event
      └──────── same transaction ────────┘
```

查询接口仍可以返回 `Map`，因为现有 MyBatis 页面投影大量使用动态列；所有写接口必须保持强类型直到应用服务边界，只有 Mapper 适配层可以组装持久化参数。

## 6. 组件设计

### 6.1 命令模型

`law-business` 提供并由 Controller、Facade、Service 和 Todo Handler 共同使用：

- `CaseAssignmentCommand`：单次分案，包含 `caseId`、主协办律师、分配方式、优先级、工作量和通知标记。
- `CaseBatchAssignmentCommand`：批量分案，包含非空 `caseIds` 和与单次分案一致的分配事实。
- `CaseTransferCommand`：转案申请，只包含客户端可提交的案件、目标律师、原因、风险和详情；申请人由 `BusinessActor` 提供。
- `CaseTransferApprovalCommand`：转案记录、审批动作和意见。
- `CaseConfirmCommand`：确认记录、接受/拒绝结果和备注。
- `LawyerProfileSaveCommand`、`LawyerProfileStatusCommand`：律师档案和可分案状态。

客户端不得提交 `applicantId`、`applicantName`、`mainLawyerName`、`handlerId`、`handlerName`、目标案件状态或事件键。Service 从当前 actor 和数据库事实派生这些值。

为保持兼容，现有 URL 和 JSON 字段不变。批量分案继续使用 `/case/assign/batch`，单次分案继续使用 `/case/assign`。

### 6.2 `CaseAccessPolicy`

新增统一对象访问策略，并由查询和全部写 Service 使用：

- `requireReadable(caseId, actor)`：案件存在、未删除，并在管理员或角色数据范围内。
- `requireAssignable(caseId, actor)`：可读且状态为 `pending`。
- `requireTransferRequestable(caseId, actor)`：可读、状态为 `processing`，且非管理员操作人必须是案件 owner 或当前主办律师。
- `requireTransferApprovable(transferId, actor)`：转案存在、关联案件可读、转案状态为 `pending`。
- `requireConfirmable(confirmId, actor)`：确认记录存在、状态为 `pending`、案件状态为 `confirming`；非管理员必须等于 `confirm_user_id`。

Controller 的 `@PreAuthorize` 继续负责功能权限，AccessPolicy 负责对象和人员权限。Todo Handler 传入明确的 `BusinessActor`，不能依赖线程中隐含的前端参数。

### 6.3 案件生成

`CaseCreationService` 以 `contractId + BusinessActor` 为入口，并在事务中重新读取合同，避免调用方传入过期合同快照。

使用现有事实校验：

- 合同存在且未删除。
- 合同已签署。
- 合同至少存在一笔 `confirmed` 的收费计划，不能仅依赖 Todo 触发顺序隐含缴费事实。
- 调用来自现有 `CASE_CREATE_CHECK` 时，材料核验仍由 `ContractTodoValidator` 完成。
- `biz_case.contract_id` 的现有唯一约束作为最终并发屏障。

若合同已存在案件，返回已有业务事实且不重复写状态日志、不重复发事件。并发插入未获胜的一方同样按已有案件处理，不把唯一键冲突暴露为第二个案件或第二条事件。

案件生成成功后的事件键固定为：

```text
CASE_CREATED:{caseId}
```

### 6.4 初次分案和批量分案

单次分案事务顺序：

1. AccessPolicy 校验案件和 `pending` 状态。
2. 校验主办律师、协办律师、字典和工作量。
3. 条件更新案件 `pending → confirming/processing`。
4. 插入 `biz_case_assignment` 并取得 `assignment_id`。
5. 需要确认时插入 `biz_case_confirm` 并取得 `confirm_id`。
6. 写状态日志和现有通知。
7. 写 Outbox。

事件键固定为：

```text
CASE_ASSIGNED:{caseId}:{assignmentId}
```

payload 至少包含 `assignmentId`、`confirmId`、`mainLawyerId` 和 `needConfirm`。

批量分案保持一个外层事务；任意案件校验或写入失败时整批回滚，避免前端收到失败但部分案件已完成分配。每个案件仍形成自己的 assignment、日志和事件。

### 6.5 律师转案申请与审批

申请事务：

1. AccessPolicy 验证案件、处理状态和申请人关系。
2. 校验目标律师不是当前主办律师且具备主办资格。
3. 插入 `biz_case_transfer` 并取得 `transfer_id`。
4. 条件更新案件 `processing → transfering`。
5. 写状态日志、通知和 Outbox。

事件键固定为：

```text
CASE_TRANSFER_REQUESTED:{caseId}:{transferId}
```

审批事务：

1. AccessPolicy 验证转案和关联案件。
2. 仅接受现有 `passed`、`rejected`、`supplement` 动作。
3. 条件更新转案记录 `pending → action`。
4. 根据现有语义更新案件：通过进入 `confirming`；驳回或补充回到 `processing`。
5. 通过时创建拟转入律师的确认记录。
6. 写状态日志、通知和 Outbox。

事件键固定为：

```text
CASE_TRANSFER_APPROVED:{caseId}:{transferId}
```

事件 payload 包含 `transferId`、`confirmId`、`action`、`mainLawyerId` 和 `requiresAcceptance`。事件类型名称保持现状，即使动作是驳回或补充也不新增事件类型。

### 6.6 接案确认

AccessPolicy 必须验证当前 actor 是确认记录指定人员或管理员。确认记录和案件均使用精确条件更新：

- 确认记录必须为 `pending`。
- 案件必须为 `confirming`。
- 接受：`confirming → processing`。
- 拒绝：`confirming → pending`，按现有行为清空分案信息，回到待重新分案。

事件键固定为：

```text
CASE_ACCEPTED:{caseId}:{confirmId}
CASE_REJECTED:{caseId}:{confirmId}
```

Todo Handler 继续在接受后取消同案件其他活动待办，但业务确认失败时不得先取消待办。

### 6.7 状态和错误

所有服务先通过 `CaseStatusTransitions` 校验转换，再执行 Mapper 条件更新。非法转换统一返回 `STATE_CONFLICT`；条件更新为 0 返回 `CONCURRENT_MODIFICATION`。

统一使用已有 `BusinessErrorCode`：

- `VALIDATION_FAILED`
- `DATA_NOT_FOUND`
- `ACCESS_DENIED`
- `PRECONDITION_FAILED`
- `STATE_CONFLICT`
- `CONCURRENT_MODIFICATION`

不再在案件服务中新建自由文本错误类别。

### 6.8 案件中心前端

修复 `ruoyi-ui/src/views/case/index.vue` 时以最后一个合法 UTF-8 版本为语义基线，重新连接现有：

- `case-page-actions.js`
- `CaseActionDialogs.vue`
- `CaseResourceDrawers.vue`
- `case-page.scss`

保留待分案、分配记录、转案、律师、接案确认和状态记录模式，不新增 v0.2 页面。修复后增加源码编码检查，使用 fatal UTF-8 解码扫描 `ruoyi-ui/src` 下的 `.vue`、`.js` 和 `.scss`，并纳入前端测试或 CI。

实际页面 E2E 至少验证：

- 待分案页面打开分案弹窗并提交兼容 payload。
- 转案页面发起申请并提交审批。
- 接案确认页面只发送 `confirmId`、`confirmResult` 和备注。
- 页面没有 `pageerror` 或 `console.error`。

## 7. 事务与幂等

每个业务动作必须满足：

```text
业务表变更 + 业务动作记录 + 状态日志 + Outbox 写入 = 同一事务
```

Outbox 写入失败必须回滚此前的案件、分案、转案或确认变更。重复请求由状态条件更新和业务唯一事实阻止；稳定事件键使重复事件不会生成重复待办。

不通过随机 UUID 区分同一业务动作。随机值可以继续用于与幂等无关的展示编号，但不能进入事件幂等键。

## 8. 测试策略

### 8.1 单元测试

- 命令校验和客户端不可控字段。
- CaseAccessPolicy 的数据范围、主办律师转案和指定确认人。
- 案件生成已存在/并发重复。
- 分案、批量回滚、转案申请、审批和接案的状态转换。
- 每个事件的稳定键和 payload。
- Mapper 条件更新为 0 时返回稳定业务错误码。

### 8.2 集成测试

新增覆盖现有事实链路的事务测试：

```text
合同签署/缴费事实
→ 案件生成
→ 初次分案
→ 律师接案
→ 发起转案
→ 转案审批
→ 新律师接案
```

测试断言业务状态、记录 ID、事件顺序和稳定键。另用可回滚事务夹具证明 Outbox 抛错时业务记录、状态和日志一起回滚。

### 8.3 结构与前端测试

- `BizCaseServiceImpl` 不直接依赖 Mapper。
- 案件 Controller、Facade、Service、Todo Handler 的写边界不再暴露 `Map`。
- 案件相关 Service 不再生成随机事件键。
- 案件中心源文件均为合法 UTF-8。
- 前端生产构建和完整 Playwright 套件通过。

### 8.4 全量门禁

- `mvn --batch-mode --no-transfer-progress clean verify`
- 前端源码编码检查。
- `npm run test:todo`
- 前端生产构建。
- 完整 Playwright。
- 有测试数据库时执行 Flyway、数据库不变量和 Todo 事务测试；本机未配置测试库时必须如实记录 skipped，不能记为通过。

## 9. 完成标准

本子项目只有在以下证据同时成立时完成：

- 案件模块所有写入口保持强类型到 Service。
- CaseAccessPolicy 覆盖读取、分案、转案和接案对象权限。
- 六类现有案件事件全部使用稳定业务键。
- 状态更新均有精确前置状态和并发冲突错误。
- Outbox 失败回滚测试通过。
- 案件中心页面恢复合法 UTF-8，真实页面 E2E 通过。
- 全量后端、前端和范围审计通过。
- 没有新增 v0.2 表、字段、字典、模板或事件类型。

达到这些条件只表示“缴费 → 商务交案/案件生成 → 分案 → 律师转案 → 案管接收”现有业务底座完成治理，不表示 PRD 的转案材料、案管分类或整个 v0.2 已实现。
