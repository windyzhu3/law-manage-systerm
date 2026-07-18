# G-04 历史迁移预检与异常清单设计

## 1. 目标

在不选择历史案件默认业务线、不新增 `biz_case.business_line`、不修改历史案件或 Todo 实例的前提下，为 G-04 提供可重复执行的真实数据盘点、异常候选清单和 SHA-256 证据，使产品、案管、架构与 DBA 能基于同一份事实输入完成后续决策和独立评审。

本切片只让“证据可以被生成和复核”更真实，不把 `HISTORICAL_CASE_DEFAULT` 或四项 `NEEDS_EVIDENCE` 自动提升为 `CONFIRMED`，也不批准 `G04-HISTORICAL-MIGRATION`。

## 2. 明确边界

### 2.1 本次包含

- 实时读取历史案件、删除案件、历史 Todo 和孤儿模板版本引用数量；
- 按案件状态与案件类型分组显示历史案件分布；
- 把当前全部有效历史案件导出为待人工分类的异常候选清单；
- 生成稳定排序、确定性编码的 CSV，并提供行数和 SHA-256；
- 前端展示预检统计、分组结果、导出边界及校验和；
- 独立导出权限、操作日志、敏感响应头与临时文件清理；
- 真实 MySQL 证明导出前后业务表和 Todo 表未被修改。

### 2.2 本次不包含

- 选择 `NON_LITIGATION`、`COMPREHENSIVE` 或 `EXECUTION` 中的任何默认值；
- 自动推断或填写 `proposed_business_line`；
- 新增或回填 `biz_case.business_line`；
- 创建迁移批次、异常或审计业务表；
- 修改 `todo_instance.template_version_id` 或运行时快照；
- 更新 G-04 要求来源状态、准入证据状态或独立 Reviewer 结论；
- 把导出文件当成已经签字的不可变证据。

## 3. 架构与组件

### 3.1 后端边界

在 `law-todo` 中新增两个职责分离的应用服务：

1. `TodoHistoricalMigrationPreflightService`
   - 只读取汇总、案件分组和运行态不变量；
   - 返回 `TodoHistoricalMigrationPreflightView`；
   - 使用现有 G-04 Mapper 命名空间的新增只读查询；
   - 不复用业务写 Service，不持有任何写 Mapper 方法。

2. `TodoHistoricalMigrationExportService`
   - 在只读事务的一致性快照内流式读取有效案件；
   - 把数据库游标交给专用归档写入器；
   - 返回内部 `HistoricalMigrationExportArtifact`，包含临时文件、ZIP 大小、候选行数、CSV SHA-256 和生成时间；
   - 不包含 HTTP、CSV 转义或临时路径实现细节。

3. `HistoricalMigrationExportArchiveWriter`
   - 生成临时 ZIP，内含 `historical-case-exceptions.csv` 和 `manifest.json`；
   - 负责 CSV 编码、公式注入保护、SHA-256、专用临时根目录和路径约束；
   - 提供受控读取和幂等删除操作；
   - Controller 传输完成或失败后必须调用删除；
   - 临时路径不进入 API、日志或错误消息。

新增 API：

```text
GET /todo/foundation-migration/preflight?gateCode=G-04
GET /todo/foundation-migration/exception-export?gateCode=G-04
```

预检需要 `todo:admission:view`；导出需要新增的 `todo:admission:export`。导出 Controller 使用 RuoYi 操作日志记录导出人、时间、结果和行数，但不记录案件名称或文件正文。

### 3.2 前端边界

扩展现有 `HistoricalMigrationReadiness.vue`，不增加新的顶级菜单：

- 在原有 8 项准入目录上方增加“历史数据预检”区域；
- 展示有效/删除案件、历史 Todo、孤儿版本、异常候选数量；
- 展示 `caseStatus + caseType` 分组表；
- 仅拥有 `todo:admission:export` 时显示“导出异常候选清单”；
- 下载成功后显示服务端返回的候选行数和 CSV SHA-256，供 Reviewer 独立复算；
- 明确提示“导出清单尚未分类、尚未签字、不会自动改变 G-04”。

页面继续复用配置中心加载流程；预检失败不覆盖现有 G-04 要求列表，避免一次附加查询导致整个配置页不可用。

## 4. 数据契约

### 4.1 预检响应

`TodoHistoricalMigrationPreflightView`：

```text
gateCode: String
generatedAt: Instant
activeCaseCount: long
deletedCaseCount: long
historicalTodoCount: long
orphanTodoVersionCount: long
exceptionCandidateCount: long
groups: List<HistoricalCaseGroupView>
```

`HistoricalCaseGroupView`：

```text
caseStatus: String
caseType: String
caseCount: long
```

空状态或类型统一输出 `<NULL>`，分组排序固定为 `caseCount DESC, caseStatus ASC, caseType ASC`。

### 4.2 CSV

CSV 列顺序固定为：

```text
case_id,case_no,case_name,case_type,case_status,contract_id,main_lawyer_id,dept_id,
proposed_business_line,exception_reason,review_status,reviewer_user_id,reviewed_at
```

生成规则：

- 只导出 `biz_case.del_flag='0'`；
- 按 `case_id ASC`；
- `proposed_business_line`、`reviewer_user_id`、`reviewed_at` 留空；
- `exception_reason=NO_REVIEWED_CLASSIFICATION`；
- `review_status=PENDING`；
- 使用 UTF-8 BOM、RFC 4180 双引号和 CRLF；
- 以 `=`, `+`, `-`, `@`, 制表符或回车开头的文本单元格前加单引号，阻止表格公式注入；
- CSV SHA-256 对包含 BOM 的完整字节计算；
- 相同数据库快照必须产生相同 CSV 字节和哈希。

### 4.3 manifest.json

Manifest 至少包含：

```json
{
  "schemaVersion": 1,
  "gateCode": "G-04",
  "fileName": "historical-case-exceptions.csv",
  "rowCount": 0,
  "csvSha256": "64 lowercase hex characters",
  "generatedAt": "ISO-8601 instant",
  "classificationState": "UNREVIEWED",
  "allowedBusinessLines": ["NON_LITIGATION", "COMPREHENSIVE", "EXECUTION"]
}
```

`generatedAt` 不进入 CSV，因此不影响 CSV 的确定性哈希。ZIP 本身不作为跨次执行的确定性产物；Reviewer 复核的是 CSV 行数和 `csvSha256`。

响应头：

```text
Content-Type: application/zip
Content-Disposition: attachment; filename="g04-historical-case-preflight.zip"
X-Exception-Row-Count: <count>
X-Exception-CSV-SHA256: <sha256>
Cache-Control: no-store
X-Content-Type-Options: nosniff
```

## 5. 一致性、安全与错误处理

- 预检与导出只允许 `gateCode=G-04`，其他值返回稳定错误 `TODO_MIGRATION_GATE_UNSUPPORTED`；
- 导出使用只读事务和单一一致性快照，汇总行数与 CSV 行数必须一致；
- 数据库游标逐行写临时文件，禁止把全部历史案件加载进内存；
- 临时目录由配置项控制，适配器校验解析后路径仍在专用根目录内；
- 正常响应、客户端断开和运行时异常均触发临时文件清理；
- CSV/ZIP 生成失败返回 `TODO_MIGRATION_EXPORT_FAILED`，不暴露 SQL、临时路径或案件正文；
- 导出接口不接受默认业务线、案件 ID 列表或任意 SQL 条件，防止绕过完整盘点；
- 预检和导出不得执行 `INSERT`、`UPDATE`、`DELETE`、DDL 或 G-04 状态变更。

## 6. 数据库与权限迁移

新增一个前向 Flyway，仅登记权限：

```text
todo:admission:export
```

该迁移不得：

- 修改 `biz_case`；
- 新增迁移批次/异常/审计表；
- 修改 `todo_foundation_migration_requirement.source_status`；
- 修改 `todo_admission_evidence.status`；
- 修改 Q-001 或其他决策。

既有角色是否获得导出权限继续由管理员显式授权，不在迁移中自动批量授予。

## 7. 测试与验收

### 7.1 后端单元/契约测试

- 预检服务准确映射统计和分组，空值输出 `<NULL>`；
- 非 G-04 请求被拒绝；
- CSV 列顺序、排序、BOM、CRLF、引号、空字段与公式注入保护准确；
- Manifest 行数、允许值、状态和 CSV SHA-256 准确；
- 大批量输入通过游标逐行处理，不调用全量列表查询；
- 导出成功、传输失败和生成失败均删除临时文件；
- Flyway 契约证明只新增导出权限，未触碰业务表和准入状态。

### 7.2 真实 MySQL E2E

从空 v0.15 基线运行完整 Flyway 后：

1. 插入覆盖空值、中文、引号、换行和公式前缀的案件夹具；
2. 记录 `biz_case` 行级校验和、`todo_instance` 行数和孤儿版本数；
3. 调用预检并断言汇总/分组；
4. 调用导出，解压 CSV/Manifest 并复算 SHA-256；
5. 再次计算业务数据校验和，必须与导出前完全一致；
6. 断言 G-04 仍为原始来源状态、证据仍 `OPEN`、Foundation 仍 `NOT_ADMITTED`。

### 7.3 前端契约与 E2E

- 配置页局部加载预检数据；
- 无导出权限不显示按钮；
- 有权限时下载 Blob 并展示行数与哈希；
- 预检失败只显示局部错误，不清空既有 G-04 要求；
- 页面持续显示未决/未签字警告，不出现“G-04 已完成”误导文案。

## 8. 完成判定

本切片完成仅表示：

- 真实历史数据可以被只读盘点；
- 待人工分类清单可以安全导出并复核哈希；
- 前后端、权限、审计、临时文件和真实 MySQL 证据齐备；
- 业务数据和 Todo 实例未被修改。

本切片完成后，G-04 仍应保持未准入。只有 Q-001、异常清单、架构/案管/DBA 演练与签字、后续结构/回填迁移及独立 Reviewer 审批全部完成，G-04 才能关闭。
