# G-05 文件内容真实性与严格上传策略设计

## 1. 目标

在现有受治理文件中心中落实已确认的方案1：文件注册信息、HTTP multipart 信息和实际存储内容必须一致；HTML、SVG、可执行内容直接拒绝；无法可靠识别或不在允许矩阵中的内容不得成为有效文件版本。

本设计只治理 `/files` 文件中心，不修改 RuoYi 历史 `/common/upload`、头像或导入接口。后续业务模块应统一迁移到文件中心，不能把旧上传接口视为已通过 G-05。

## 2. 安全不变量

1. 注册阶段先校验文件名扩展名与声明 MIME 是否属于同一允许项，明显不一致时不创建上传意图。
2. 完成上传时再次校验 multipart 原始文件名、multipart MIME 与注册意图一致。
3. 存储适配器在临时区完成大小和 SHA-256 校验后识别实际格式；业务元数据入库前必须通过内容策略。
4. HTML、SVG、Windows PE、ELF、脚本声明类型和未知可执行内容不允许上传，即使改扩展名或声明为 `application/octet-stream`。
5. PDF、图片和纯文本等可预览类型必须有对应实际签名；响应仍保留 `nosniff`、CSP sandbox、no-referrer 和固定内联白名单。
6. Office 文件只允许识别出的 OOXML 容器或 OLE 复合文档；普通 ZIP、损坏容器和加密/无法识别容器不冒充 Office 文件。
7. 类型校验失败不得创建 `file_object_version`、激活对象或完成上传意图；临时对象必须进入既有清理补偿流程。
8. G-05 独立安全 Reviewer 签字仍是准入条件，技术实现不能自行把证据改为 APPROVED。

## 3. 允许矩阵

| 扩展名 | 允许声明 MIME | 实际识别结果 | 访问方式 |
|---|---|---|---|
| `.pdf` | `application/pdf` | PDF `%PDF-` | 可预览/下载 |
| `.png` | `image/png` | PNG 签名 | 可预览/下载 |
| `.jpg`、`.jpeg` | `image/jpeg`、`image/jpg` | JPEG 签名 | 可预览/下载，持久化规范化为 `image/jpeg` |
| `.gif` | `image/gif` | GIF87a/GIF89a | 可预览/下载 |
| `.webp` | `image/webp` | RIFF/WEBP | 可预览/下载 |
| `.txt`、`.log` | `text/plain` | 有效 UTF-8 文本、无 NUL | 可预览/下载 |
| `.csv` | `text/csv`、`text/plain` | 有效 UTF-8 文本、无 NUL | 下载；仅 `text/plain` 进入既有内联白名单 |
| `.docx` | OOXML Word MIME | ZIP 中存在 `word/document.xml` | 强制下载 |
| `.xlsx` | OOXML Excel MIME | ZIP 中存在 `xl/workbook.xml` | 强制下载 |
| `.pptx` | OOXML PowerPoint MIME | ZIP 中存在 `ppt/presentation.xml` | 强制下载 |
| `.doc` | `application/msword` | OLE 复合文档签名 | 强制下载 |
| `.xls` | `application/vnd.ms-excel` | OLE 复合文档签名 | 强制下载 |
| `.ppt` | `application/vnd.ms-powerpoint` | OLE 复合文档签名 | 强制下载 |

不接受无扩展名、双重危险扩展名、`.html/.htm/.svg/.svgz/.exe/.dll/.com/.bat/.cmd/.ps1/.js/.jar/.sh`、普通 `.zip` 或泛化 `application/octet-stream`。文件名比较大小写不敏感，只取最后一个扩展名；路径分隔符、控制字符和尾随点/空格继续由元数据校验拒绝。

## 4. 组件边界

### 4.1 `FileContentInspector`

位于 `law-file` 基础设施层，通过 `FileStoragePort.inspect(StagedObject)` 暴露不可变检测结果。`LocalFileStorageAdapter` 只读取已完成 SHA-256 校验的临时文件：

- 用固定头部识别 PDF、PNG、JPEG、GIF、WebP、OLE、PE 和 ELF；
- 用 UTF-8 严格解码和 NUL 检查识别纯文本；
- 对 ZIP 只读取中央目录的条目名称，使用 `word/document.xml`、`xl/workbook.xml`、`ppt/presentation.xml` 区分 OOXML；
- 不解压正文，不把 ZIP 条目写入磁盘，避免路径穿越和解压炸弹；
- 返回规范化的内部枚举，不信任操作系统 `probeContentType`。

### 4.2 `FileContentPolicy`

位于安全层，职责单一：把文件名、注册 MIME、multipart MIME 和检测结果与允许矩阵比较。它不读文件、不操作数据库、不发布事件。

公开行为：

- `validateRegistration(fileName, declaredContentType)`：在创建意图前验证扩展名和声明类型；
- `requireMatchingContent(fileName, declaredContentType, transportFileName, transportContentType, detectedType)`：完成上传前执行全部一致性校验并返回规范化持久化 MIME。

### 4.3 `FileObjectService`

注册和新增版本时调用注册校验。完成上传的数据流固定为：

```text
锁定上传意图
→ 重新校验业务写权限
→ 校验 multipart 元数据与意图
→ stage（大小 + SHA-256）
→ inspect（实际内容）
→ FileContentPolicy 一致性校验
→ 写不可变版本并激活
→ publish
→ 生命周期审计
```

策略失败沿用现有事务回滚和 `file_storage_cleanup` 补偿，不新建平行清理机制。

### 4.4 `FileObjectController`

`complete` 必须把 `MultipartFile.getOriginalFilename()` 和 `getContentType()` 传入 Service，不能只传文件流。错误继续由 `FileExceptionAdvice` 输出稳定业务错误码。

## 5. 错误契约

| 错误码 | 含义 | HTTP 映射 |
|---|---|---|
| `FILE_CONTENT_TYPE_BLOCKED` | 活动内容、可执行内容或明确禁止类型 | 400 |
| `FILE_CONTENT_TYPE_MISMATCH` | 扩展名、注册 MIME、multipart MIME、实际内容不一致 | 400 |
| `FILE_CONTENT_TYPE_UNSUPPORTED` | 无法可靠识别或不在允许矩阵 | 400 |
| `FILE_CONTENT_CONTAINER_INVALID` | Office/ZIP 容器损坏或无法确定具体 OOXML 类型 | 400 |

错误消息不得回显文件正文、存储路径或内部对象键。失败上传意图保持可审计但不完成，用户需要用新的 `actionId` 和正确元数据重新注册；同一个幂等键不能换内容类型重试。

## 6. 数据与兼容性

- 不新增数据库字段和 Flyway；检测结果只用于校验，持久化 `content_type` 使用允许矩阵中的规范 MIME。
- 已存在历史版本保持可读；响应层固定下载降级仍保留，防止历史活动内容被内联执行。
- 新上传和新版本统一执行严格策略。
- 前端文件组件不自行判断内容真实性；后端错误由现有上传失败消息展示。前端 `accept` 只作为体验提示，不构成安全控制。
- 本轮不引入杀毒引擎、异步隔离区、宏检测或内容消毒。这些属于安全 Reviewer 可追加的后续控制，不降低本轮三方一致性校验要求。

## 7. 测试设计

### 7.1 单元测试

- 每个允许矩阵项至少一个通过用例；
- PDF 文件名/MIME 正确但正文为 HTML：拒绝；
- `.txt` 声明 `text/plain` 但正文为 PE/ELF：拒绝；
- `.svg` 或 `image/svg+xml`：注册阶段拒绝；
- `.docx` 的 ZIP 不含 Word 核心条目：拒绝；
- OOXML Word 内容伪装成 `.xlsx`：拒绝；
- multipart 文件名或 MIME 与意图不一致：拒绝；
- 类型失败后未写版本、未激活、未完成意图，并调用临时清理。

### 7.2 真实存储测试

`LocalFileStorageAdapterTest` 使用真实临时目录验证签名识别、OOXML 中央目录识别、损坏容器和可执行签名。

### 7.3 真实 MySQL E2E

`FileMaterialEndToEndTest` 继续覆盖 21 个 PRD 材料类型的文本文件上传、预览、下载和访问审计，并增加：

- HTML/SVG/伪装可执行文件完成上传被拒绝；
- 拒绝后没有有效版本且临时对象被清理；
- 合法 PDF 和 OOXML 文件能够完成上传，预览/下载策略正确；
- G-05 仍为 6/7，`SECURITY_REVIEW_SIGNOFF` 仍为 `NEEDS_REVIEW`。

### 7.4 全量门禁

真实 MySQL 8.4 从 v0.15 基线执行完整 Flyway；运行 `mvn clean verify`、前端合同测试、编码检查、Foundation CI、生产构建和全部 Playwright E2E。准入报告只能记录实测数字，不得因技术控制完成而代替安全签字。

## 8. 验收标准

1. 新文件版本无法通过扩展名、MIME 或内容伪装绕过允许矩阵。
2. 活动内容和可执行内容在版本入库前被拒绝。
3. 合法 PDF、图片、文本、OOXML 和 OLE 文件按矩阵工作。
4. 类型失败不会留下有效版本或不可追踪临时对象。
5. 历史文件的响应级防御仍然存在。
6. 全量后端、前端、真实 MySQL 和 E2E 门禁通过。
7. G-05 保持等待独立 Reviewer，不自动批准 Foundation。
