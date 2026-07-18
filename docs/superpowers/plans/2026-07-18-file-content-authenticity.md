# Strict File Content Authenticity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce the approved G-05 strict upload policy so registered file name, declared MIME, multipart metadata and detected content all agree before a governed file version becomes active.

**Architecture:** `FileContentPolicy` owns the allow matrix and stable errors; `FileStoragePort.inspect` exposes a normalized `DetectedContentType` for an already staged object; `LocalFileStorageAdapter` performs deterministic signature/container inspection without trusting the operating system. `FileObjectService` composes registration validation, transport validation, staging, inspection and persistence while retaining existing transaction cleanup.

**Tech Stack:** Java 17, Spring Boot, MyBatis, JUnit 5, Mockito, MySQL 8.4, Flyway, Vue 2, Playwright.

## Global Constraints

- Work only on `v0.2-Foundation`; commit locally and do not push without explicit user instruction.
- Govern only `/files`; do not silently claim `/common/upload`, avatar or import endpoints satisfy G-05.
- Reject HTML, SVG, PE, ELF, script declarations, unknown content, generic octet-stream and generic ZIP.
- Allow only the exact matrix in `docs/superpowers/specs/2026-07-18-file-content-authenticity-design.md`.
- Never persist storage paths, object keys or file body fragments in errors.
- Keep the existing response hardening for historical versions.
- Keep `SECURITY_REVIEW_SIGNOFF=NEEDS_REVIEW`, G-05 at 6/7 and aggregate admission at `2/8 NOT_ADMITTED`.
- Every production behavior follows RED/GREEN TDD.

---

### Task 1: Pure content policy

**Files:**
- Create: `law-file/src/main/java/com/law/file/security/DetectedContentType.java`
- Create: `law-file/src/main/java/com/law/file/security/FileContentPolicy.java`
- Create: `law-file/src/test/java/com/law/file/security/FileContentPolicyTest.java`

**Interfaces:**
- Produces: `DetectedContentType` enum and `FileContentPolicy.validateRegistration(String,String)` / `requireMatchingContent(String,String,String,String,DetectedContentType)`.
- Returns: canonical MIME string from `requireMatchingContent`.

- [ ] **Step 1: Write failing policy tests**

Cover every allow-matrix entry plus registration blocks and mismatches. Representative assertions:

```java
assertEquals("application/pdf",policy.requireMatchingContent(
    "proof.pdf","application/pdf","proof.pdf","application/pdf",DetectedContentType.PDF));
assertCode("FILE_CONTENT_TYPE_BLOCKED",()->policy.validateRegistration("active.svg","image/svg+xml"));
assertCode("FILE_CONTENT_TYPE_MISMATCH",()->policy.requireMatchingContent(
    "proof.pdf","application/pdf","proof.pdf","application/pdf",DetectedContentType.HTML));
assertCode("FILE_CONTENT_TYPE_UNSUPPORTED",()->policy.validateRegistration("archive.zip","application/zip"));
```

The test must enumerate PDF, PNG, JPEG aliases, GIF, WebP, TXT, LOG, CSV, DOCX, XLSX, PPTX, DOC, XLS and PPT.

- [ ] **Step 2: Run RED**

Run:

```text
mvn -pl law-file -am -Dtest=FileContentPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: test compilation fails because `DetectedContentType` and `FileContentPolicy` do not exist.

- [ ] **Step 3: Implement the enum and pure policy**

`DetectedContentType` contains exactly:

```java
PDF,PNG,JPEG,GIF,WEBP,UTF8_TEXT,OOXML_WORD,OOXML_EXCEL,OOXML_POWERPOINT,
OLE_COMPOUND,HTML,SVG,PE_EXECUTABLE,ELF_EXECUTABLE,GENERIC_ZIP,UNKNOWN
```

`FileContentPolicy` uses immutable maps keyed by lower-case final extension. It rejects path separators, control characters, trailing dot/space, missing extension and dangerous earlier extensions such as `invoice.pdf.exe`. It normalizes MIME parameters and `image/jpg` to `image/jpeg`, validates the transport name/MIME against the registered canonical values, and throws the four errors defined by the approved spec.

- [ ] **Step 4: Run GREEN**

Run the command from Step 2. Expected: all `FileContentPolicyTest` tests pass.

- [ ] **Step 5: Commit Task 1**

```text
git add law-file/src/main/java/com/law/file/security/DetectedContentType.java law-file/src/main/java/com/law/file/security/FileContentPolicy.java law-file/src/test/java/com/law/file/security/FileContentPolicyTest.java
git commit -m "feat(file): enforce strict content type policy"
```

---

### Task 2: Deterministic staged-content inspection

**Files:**
- Modify: `law-file/src/main/java/com/law/file/spi/FileStoragePort.java`
- Create: `law-file/src/main/java/com/law/file/infrastructure/FileContentInspector.java`
- Modify: `law-file/src/main/java/com/law/file/infrastructure/LocalFileStorageAdapter.java`
- Create: `law-file/src/test/java/com/law/file/infrastructure/FileContentInspectorTest.java`
- Modify: `law-file/src/test/java/com/law/file/infrastructure/LocalFileStorageAdapterTest.java`

**Interfaces:**
- Consumes: `DetectedContentType` from Task 1.
- Produces: `FileStoragePort.inspect(StagedObject): DetectedContentType`.

- [ ] **Step 1: Write failing signature and container tests**

Use real byte arrays and temporary files. Tests must prove:

```java
assertEquals(DetectedContentType.PDF,inspect(bytes("%PDF-1.7\n")));
assertEquals(DetectedContentType.PE_EXECUTABLE,inspect(new byte[]{'M','Z',0,0}));
assertEquals(DetectedContentType.ELF_EXECUTABLE,inspect(new byte[]{0x7f,'E','L','F'}));
assertEquals(DetectedContentType.HTML,inspect(bytes("<!doctype html><html>")));
assertEquals(DetectedContentType.SVG,inspect(bytes("<svg xmlns=\"http://www.w3.org/2000/svg\">")));
```

Create small ZIP fixtures whose entries are respectively `word/document.xml`, `xl/workbook.xml`, `ppt/presentation.xml`, and `payload.bin`; expect the three OOXML values and `GENERIC_ZIP`. Add corrupt ZIP and invalid UTF-8 expectations.

- [ ] **Step 2: Run RED**

```text
mvn -pl law-file -am -Dtest=FileContentInspectorTest,LocalFileStorageAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because the inspector and storage-port method are absent.

- [ ] **Step 3: Implement bounded deterministic inspection**

`FileContentInspector.inspect(Path)` checks strong signatures in this order: PE/ELF, PDF, PNG, JPEG, GIF, WebP, OLE, ZIP/OOXML, then text/HTML/SVG. ZIP inspection opens `ZipFile`, reads entry names only, rejects multiple OOXML families as `GENERIC_ZIP`, and never extracts entries. Text inspection streams the complete file through a UTF-8 decoder with malformed/unmappable input set to `REPORT`, rejects NUL, and holds at most an 8192-byte text prefix for HTML/SVG classification.

`LocalFileStorageAdapter.inspect` requires `.staged/` and a regular temporary file, then delegates to the inspector. It maps I/O/container failures to `FILE_CONTENT_CONTAINER_INVALID` without exposing the path.

- [ ] **Step 4: Run GREEN**

Run Step 2. Expected: all inspector and storage tests pass.

- [ ] **Step 5: Commit Task 2**

```text
git add law-file/src/main/java/com/law/file/spi/FileStoragePort.java law-file/src/main/java/com/law/file/infrastructure/FileContentInspector.java law-file/src/main/java/com/law/file/infrastructure/LocalFileStorageAdapter.java law-file/src/test/java/com/law/file/infrastructure/FileContentInspectorTest.java law-file/src/test/java/com/law/file/infrastructure/LocalFileStorageAdapterTest.java
git commit -m "feat(file): inspect staged content signatures"
```

---

### Task 3: Upload pipeline integration and cleanup

**Files:**
- Modify: `law-file/src/main/java/com/law/file/application/FileObjectService.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/file/FileObjectController.java`
- Modify: `law-file/src/test/java/com/law/file/application/FileObjectServiceTest.java`
- Modify: `law-file/src/test/java/com/law/file/application/FileObjectGovernanceReviewTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/file/FileObjectControllerTest.java`

**Interfaces:**
- Consumes: `FileContentPolicy` and `FileStoragePort.inspect`.
- Produces: `completeUpload(String,InputStream,String,String,FileActor)`.

- [ ] **Step 1: Write failing registration, transport and cleanup tests**

Add tests that prove registration rejects SVG before repository writes, completion rejects a detected mismatch before `insertVersion`, and staged cleanup runs after rollback. The successful test uses:

```java
when(storage.inspect(staged)).thenReturn(DetectedContentType.PDF);
FileVersion result=service.completeUpload("intent-1",input,"proof.pdf","application/pdf",actor);
```

Controller test verifies `MultipartFile.getOriginalFilename()` and `getContentType()` reach the new service signature.

- [ ] **Step 2: Run RED**

```text
mvn -pl ruoyi-admin -am -Dtest=FileObjectServiceTest,FileObjectGovernanceReviewTest,FileObjectControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation/signature failures because the service/controller integration is absent.

- [ ] **Step 3: Implement minimal pipeline integration**

Inject `FileContentPolicy` into `FileObjectService`. Call `validateRegistration` before access/repository work in both registration methods. Replace the completion signature with:

```java
public FileVersion completeUpload(String uploadIntentId,InputStream input,
    String transportFileName,String transportContentType,FileActor actor)
```

After `stage`, call `storage.inspect`, then `requireMatchingContent`; use the returned canonical MIME in `FileVersion`. Keep policy failures inside the existing catch so rollback cleanup is registered. The controller passes multipart metadata and never trusts it independently.

- [ ] **Step 4: Run GREEN and the law-file suite**

```text
mvn -pl ruoyi-admin -am -Dtest=FileObjectServiceTest,FileObjectGovernanceReviewTest,FileObjectControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl law-file -am test
```

Expected: all selected tests and all law-file tests pass.

- [ ] **Step 5: Commit Task 3**

```text
git add law-file/src/main/java/com/law/file/application/FileObjectService.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/file/FileObjectController.java law-file/src/test/java/com/law/file/application/FileObjectServiceTest.java law-file/src/test/java/com/law/file/application/FileObjectGovernanceReviewTest.java ruoyi-admin/src/test/java/com/ruoyi/web/controller/file/FileObjectControllerTest.java
git commit -m "feat(file): verify content before activating versions"
```

---

### Task 4: Real MySQL material E2E and security evidence

**Files:**
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/FileMaterialEndToEndTest.java`
- Modify: `doc/reviews/v0.2-foundation-g05-file-security-review-package.md`

**Interfaces:**
- Consumes: strict upload pipeline from Tasks 1-3.
- Produces: real storage/database evidence for valid PRD materials and blocked spoofed content.

- [ ] **Step 1: Write failing E2E expectations**

Keep all 21 PRD material types as valid UTF-8 `.txt` uploads. Replace the old active-HTML forced-download upload with assertions that completion throws `FILE_CONTENT_TYPE_BLOCKED`, no version exists and the staged directory is empty. Add a valid PDF fixture and an HTML-as-PDF mismatch. Add an in-memory DOCX ZIP with `word/document.xml` and assert successful forced download.

- [ ] **Step 2: Run the E2E evidence against disposable MySQL 8.4**

Initialize the exact v0.15 baseline in CI order, export `TODO_MIGRATION_DB_URL/USER/PASSWORD`, then run:

```text
mvn -pl ruoyi-admin -am -Dtest=FileMaterialEndToEndTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the already TDD-driven production pipeline from Tasks 1-3 satisfies the broader real-storage/database evidence. A failure is a coverage-discovered defect and must first be reduced to a new failing unit/integration regression test before production code is changed.

- [ ] **Step 3: Complete E2E fixtures and evidence text**

Use real `LocalFileStorageAdapter`, real MyBatis repository and exact SHA-256 values. Update the review package MIME row to cite `FileContentPolicyTest`, `FileContentInspectorTest` and `FileMaterialEndToEndTest`; state that signature/mismatch controls now exist while independent Reviewer/signature remains missing.

- [ ] **Step 4: Run GREEN**

Run Step 2. Expected: all material, spoofing, cleanup and Office-container scenarios pass with zero skips.

- [ ] **Step 5: Commit Task 4**

```text
git add ruoyi-admin/src/test/java/com/ruoyi/web/migration/FileMaterialEndToEndTest.java doc/reviews/v0.2-foundation-g05-file-security-review-package.md
git commit -m "test(file): prove strict content validation end to end"
```

---

### Task 5: Full verification and truthful admission report

**Files:**
- Modify: `doc/v0.2-foundation-admission-report.md`
- Modify: this plan file

**Interfaces:**
- Produces: measured completion evidence without approving G-05.

- [ ] **Step 1: Run full backend verification**

Against a fresh disposable MySQL 8.4 initialized from the exact v0.15 baseline, run `mvn clean verify`. Parse Surefire XML totals by module and require zero failures, errors and skips.

- [ ] **Step 2: Run full frontend verification**

From `ruoyi-ui`, run:

```text
npm run test:todo
npm run test:todo-schema
npm run test:encoding
npm run test:foundation-ci
npm run build:prod
npm run test:e2e
```

Expected: all contracts/build succeed and all 31 current Playwright tests pass unless this slice intentionally adds a test.

- [ ] **Step 3: Query live admission truth**

Verify Flyway remains `0.20.26`; G-05 remains six `CONFIRMED` plus one `NEEDS_REVIEW`; five admission evidences and twelve decisions remain OPEN; aggregate remains `2/8 NOT_ADMITTED`.

- [ ] **Step 4: Update report and plan from fresh evidence**

Record new exact Maven totals, strict content controls and unchanged sign-off blocker. Do not state G-05 PASS.

- [ ] **Step 5: Audit and commit locally**

Run `git diff --check`, inspect staged paths, remove the disposable database, and commit:

```text
docs(file): record strict content validation evidence
```

Do not push.
