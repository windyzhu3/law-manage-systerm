# Foundation File Access UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the approved unified-file-component contract by carrying relation authorization into Todo material views and adding permission-aware preview, download and version inspection to `BusinessFilePicker`.

**Architecture:** The existing file center remains authoritative for access checks, short-lived single-use tokens, immutable versions and auditing. Backend read models expose the already-authorized `relationId`; upload registration returns the relation it creates. The Vue component requests a PREVIEW or DOWNLOAD token only at click time, consumes it through the authenticated Axios client as a Blob, and never stores a reusable URL or raw token.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Mockito, Vue 2.6, Element UI, Axios, file-saver, Playwright.

## Global Constraints

- Work only on `v0.2-Foundation`; commit locally and do not push without explicit user instruction.
- Reuse the existing `/files/{id}/preview-token`, `/download-token`, `/versions` and `/files/access/{token}` APIs.
- Do not weaken object/relation authorization, two-minute token expiry, single-use consumption or stream audit behavior.
- Never place the raw storage key, token or permanent URL in Todo action payloads.
- Files without a positive `relationId` display an authorization warning and do not offer preview/download actions.
- All production behavior follows RED/GREEN TDD.

---

### Task 1: Preserve relation authorization in backend views

**Files:**
- Modify: `law-file/src/test/java/com/law/file/application/FileObjectServiceTest.java`
- Modify: `law-file/src/main/java/com/law/file/application/FileObjectService.java`
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoQueryServiceTest.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/view/TodoFormView.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoQueryService.java`

**Interfaces:**
- Produces: `UploadIntentView.relationId()` for an initial upload and `TodoFormView.MaterialState.relationId()` for current business materials.
- Preserves: all existing JSON property names and fields; the new fields are additive.

- [x] **Step 1: Write failing relation-id tests**

Add these assertions before changing production records:

```java
assertEquals(1L, registered.relationId());
assertEquals(12L, form.materials().get(0).relationId());
```

The registration test must mock the inserted file object, relation, relation action/lifecycle writes and upload intent write. The Todo query test already supplies `FileMaterialView(12L,41L,...)`.

- [x] **Step 2: Run the focused tests and verify RED**

Run:

```powershell
mvn -pl law-file,law-todo -am "-Dtest=FileObjectServiceTest,TodoQueryServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: test compilation fails because neither view exposes `relationId()`.

- [x] **Step 3: Implement the additive relation contract**

Use these record shapes:

```java
public record UploadIntentView(String uploadIntentId,Long fileObjectId,Long relationId,
    int versionNo,String status,Instant expiresAt) { }

public record MaterialState(Long relationId,Long fileObjectId,String materialType,String fileName) { }
```

Initial registration returns the inserted relation ID. Idempotent registration replay resolves the active relation for the same file object; a version-only upload returns a null relation ID. `TodoQueryService.materials` maps `FileMaterialView.relationId()` without re-querying or bypassing policy checks.

- [x] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2. Expected: all selected tests pass.

### Task 2: Add authenticated file-access APIs and component behavior

**Files:**
- Modify: `ruoyi-ui/scripts/check-todo-ui.js`
- Modify: `ruoyi-ui/e2e/todo-foundation-file.spec.js`
- Modify: `ruoyi-ui/src/api/todo.js`
- Modify: `ruoyi-ui/src/components/BusinessFile/BusinessFilePicker.vue`
- Modify: `ruoyi-ui/src/components/BusinessFile/TodoMaterialChecklist.vue`
- Modify: `ruoyi-ui/src/views/todo/components/TodoDetailDrawer.vue`

**Interfaces:**
- Produces: `getBusinessFilePreviewToken`, `getBusinessFileDownloadToken`, `listBusinessFileVersions`, `openBusinessFileContent`.
- Component actions: `previewFile(file)`, `downloadFile(file)`, `showVersions(file)`.

- [x] **Step 1: Add failing frontend contract and Playwright coverage**

The static contract must require all four APIs and component markers for `relationId`, `预览`, `下载`, `版本` and `未授权`. Add a Playwright case whose Todo form contains:

```js
materials: [{ relationId: 91, fileObjectId: 701, materialType: 'PROOF', fileName: 'evidence.pdf' }]
```

Mock the token, Blob and version endpoints. Assert preview/download consume distinct tokens and version inspection renders `v1` plus the original filename. Also assert an item without `relationId` shows `未授权` and exposes no preview/download buttons.

- [x] **Step 2: Run frontend tests and verify RED**

Run:

```powershell
cd ruoyi-ui
npm run test:todo
npm run test:e2e -- e2e/todo-foundation-file.spec.js --grep "authorized material"
```

Expected: static contract or E2E fails because file access APIs/actions are absent.

- [x] **Step 3: Implement tokenized Blob access and version dialog**

Add API functions using the shared authenticated request client:

```js
export function getBusinessFilePreviewToken(fileObjectId, relationId) {
  return request({ url: `/files/${fileObjectId}/preview-token`, method: 'get', params: { relationId } })
}
export function openBusinessFileContent(token) {
  return request({ url: `/files/access/${encodeURIComponent(token)}`, method: 'get', responseType: 'blob' })
}
```

Implement the corresponding download-token and version-list calls. `BusinessFilePicker` must create/revoke temporary object URLs, use `saveAs` for download, close a pre-opened preview window on error, show loading per action, and render a version dialog. It may only call token APIs when both IDs are positive. `TodoMaterialChecklist` must render the same picker in read-only mode so detail drawers receive the access actions instead of reducing materials to plain text. When the detail aggregate contains an empty material array, `TodoDetailDrawer` must fall back to the authoritative form view instead of treating the empty array as populated data.

- [x] **Step 4: Run frontend tests and verify GREEN**

Run the commands from Step 2, then:

```powershell
npm run test:todo-schema
npm run test:encoding
npm run build:prod
```

Expected: all pass; only existing bundle-size warnings may remain.

### Task 3: Full regression evidence and local commit

**Files:**
- Modify: `doc/v0.2-foundation-admission-report.md`
- Modify: this plan file

**Interfaces:**
- Produces: measured regression evidence and a clean local commit.
- Does not change G-05 approval, external Reviewer fields or aggregate admission counts.

- [x] **Step 1: Run complete backend and frontend verification**

Run `mvn clean verify` against the disposable MySQL 8 v0.15 baseline, parse exact Surefire totals, run all frontend contracts, production build and all Playwright tests.

- [x] **Step 2: Update the admission report from measured facts**

Record that the reusable file component now covers upload, authorized preview/download, version inspection and missing-relation warnings. Update test totals only from fresh reports; keep G-05 at 6/7 and aggregate admission at 2/8 unless real gate data proves otherwise.

- [x] **Step 3: Audit and commit locally**

Run `git diff --check`, inspect staged file names, stop/remove the disposable database and commit:

```text
feat(file): complete authorized file access UI
```

Do not push.
