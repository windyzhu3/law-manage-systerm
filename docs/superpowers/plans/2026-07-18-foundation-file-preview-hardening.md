# Foundation File Preview Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent client-declared active content from executing as a same-origin inline file preview while preserving safe PDF, bitmap-image and plain-text previews, downloads and access auditing.

**Architecture:** `FileObjectController` remains the only HTTP response-policy boundary. It will parse the stored MIME type, allow inline rendering only for a fixed passive-type allowlist, downgrade every other PREVIEW response to `application/octet-stream` plus `attachment`, and attach explicit `nosniff`, CSP sandbox and no-referrer headers; storage, token consumption and audit behavior remain unchanged.

**Tech Stack:** Java 17, Spring Web, JUnit 5, Mockito, MySQL-backed file E2E.

## Global Constraints

- Work only on `v0.2-Foundation`; do not push without explicit user instruction.
- Preserve the existing access-token, relation authorization, stream-completion audit and single-use semantics.
- Safe inline types are exactly `application/pdf`, `image/png`, `image/jpeg`, `image/gif`, `image/webp` and `text/plain`.
- `text/html`, `application/xhtml+xml`, `image/svg+xml`, XML, script and unknown/invalid types must never be returned inline.
- Every file stream response must include `X-Content-Type-Options: nosniff`, `Content-Security-Policy: sandbox; default-src 'none'` and `Referrer-Policy: no-referrer`.
- This technical hardening does not approve G-05, assign a Reviewer or change Foundation admission counts.

---

### Task 1: Controller policy with RED/GREEN proof

**Files:**
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/file/FileObjectControllerTest.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/file/FileObjectController.java`

**Interfaces:**
- Consumes: `AccessContent.accessType()`, `contentType()` and `fileName()`.
- Produces: a safe `ResponseEntity<StreamingResponseBody>` without changing the stream or audit callbacks.

- [x] **Step 1: Write the failing active-content test**

Add a controller test whose service returns PREVIEW content declared as `text/html`. Assert:

```java
assertEquals(MediaType.APPLICATION_OCTET_STREAM,response.getHeaders().getContentType());
assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("attachment"));
assertEquals("nosniff",response.getHeaders().getFirst("X-Content-Type-Options"));
assertEquals("sandbox; default-src 'none'",response.getHeaders().getFirst("Content-Security-Policy"));
assertEquals("no-referrer",response.getHeaders().getFirst("Referrer-Policy"));
```

Also extend the existing PDF preview test to require the three security headers while remaining inline.

- [x] **Step 2: Run the focused test and verify RED**

Run:

```powershell
mvn -pl ruoyi-admin -am "-Dtest=FileObjectControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: the HTML response is still `inline`/`text/html` and the explicit headers are absent.

- [x] **Step 3: Implement the minimal response policy**

Add a private fixed MIME set and a private policy helper:

```java
private static final Set<String> INLINE_PREVIEW_TYPES=Set.of(
    "application/pdf","image/png","image/jpeg","image/gif","image/webp","text/plain");

private static ResponsePolicy responsePolicy(AccessContent content)
{
    MediaType declared=parseMediaType(content.contentType());
    String normalized=declared.getType()+"/"+declared.getSubtype();
    boolean inline="PREVIEW".equals(content.accessType())&&INLINE_PREVIEW_TYPES.contains(normalized);
    return new ResponsePolicy(inline?declared:("PREVIEW".equals(content.accessType())
        ?MediaType.APPLICATION_OCTET_STREAM:declared),inline);
}
```

Use the policy to choose inline/attachment disposition and add the three required response headers. Invalid MIME values parse as `application/octet-stream`.

- [x] **Step 4: Run the focused test and verify GREEN**

Run the command from Step 2. Expected: all `FileObjectControllerTest` methods pass.

### Task 2: Real material E2E and review evidence

**Files:**
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/FileMaterialEndToEndTest.java`
- Modify: `doc/reviews/v0.2-foundation-g05-file-security-review-package.md`
- Modify: `doc/v0.2-foundation-admission-report.md`
- Modify: this plan file

**Interfaces:**
- Consumes: the production controller response policy.
- Produces: real MySQL/MyBatis/local-storage evidence that all 21 PRD material previews retain safe inline behavior and explicit response headers, while the review package records the remaining server-side content-detection decision accurately.

- [x] **Step 1: Extend the real E2E assertions**

For each PRD material preview and download, assert `nosniff`, CSP sandbox and no-referrer. Keep the existing body, single-use-token and four-audit-row assertions.

- [x] **Step 2: Update the security review package truthfully**

Record that response-level active-content execution is mitigated by allowlist/downgrade/headers, while server-side magic-byte detection, malware scanning and capacity policy remain independent review decisions. Do not change the Reviewer or sign-off fields.

- [x] **Step 3: Run complete verification**

Run Maven full verification with the disposable real MySQL baseline, all frontend Todo/schema/encoding/CI contracts, production build, 30 Playwright tests and `git diff --check`.

- [x] **Step 4: Commit locally**

After verifying G-05 remains 6/7 and aggregate admission remains 2/8, commit with:

```text
fix(file): harden active content previews
```
