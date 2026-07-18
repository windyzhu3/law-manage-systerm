# G-04 Historical Migration Preflight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only G-04 historical-case inventory and deterministic exception-candidate export so reviewers can produce hash-verifiable evidence without selecting a default business line or mutating historical data.

**Architecture:** `TodoHistoricalMigrationPreflightService` reads counts and grouped inventory; `TodoHistoricalMigrationExportService` streams a MyBatis cursor through `HistoricalMigrationExportArchiveWriter`, which creates a cleanup-on-close ZIP containing deterministic CSV and a manifest. The existing Todo configuration page loads preflight data locally and downloads the archive through a dedicated permission while existing G-04 readiness and admission state remain unchanged.

**Tech Stack:** Java 17, Spring Boot, MyBatis Cursor, Fastjson2, MySQL 8.4, Flyway, JUnit 5, Mockito, Vue 2, Element UI, Axios, file-saver, Playwright.

## Global Constraints

- Work only on `v0.2-Foundation`; commit locally and do not push without explicit user instruction.
- Do not add or update `biz_case.business_line` and do not modify any `biz_case` or `todo_instance` row.
- Do not choose a default from `NON_LITIGATION`, `COMPREHENSIVE`, `EXECUTION` and leave every exported `proposed_business_line` blank.
- Do not update `todo_foundation_migration_requirement.source_status`, `todo_admission_evidence.status`, Q-001, Reviewer identity or sign-off evidence.
- Only `gateCode=G-04` is supported; reject every other value with `TODO_MIGRATION_GATE_UNSUPPORTED`.
- Export all `biz_case.del_flag='0'` rows ordered by `case_id ASC`; do not accept caller-defined filters or case IDs.
- Generate UTF-8 BOM/RFC 4180 CSV with CRLF and spreadsheet-formula injection protection; hash the complete CSV bytes including BOM.
- Stream the database cursor and use bounded temporary files; never load all historical cases into memory and never expose temporary paths.
- The response must be `no-store`/`nosniff`; success, generation failure and client disconnect must all clean temporary files.
- Every production behavior follows RED/GREEN TDD.

---

### Task 1: Read-only preflight inventory

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/view/HistoricalCaseGroupView.java`
- Create: `law-todo/src/main/java/com/law/todo/application/view/TodoHistoricalMigrationPreflightView.java`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoHistoricalMigrationPreflightService.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoHistoricalMigrationReadinessMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoHistoricalMigrationReadinessMapper.xml`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoHistoricalMigrationReadinessController.java`
- Create: `law-todo/src/test/java/com/law/todo/application/TodoHistoricalMigrationPreflightServiceTest.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/todo/HistoricalMigrationPreflightApiTest.java`

**Interfaces:**
- Produces: `TodoHistoricalMigrationPreflightService.preflight(String): TodoHistoricalMigrationPreflightView`.
- Mapper produces: `Map<String,Object> selectHistoricalMigrationPreflightCounts()` and `List<Map<String,Object>> selectHistoricalCaseGroups()`.
- HTTP produces: `GET /todo/foundation-migration/preflight?gateCode=G-04` protected by `todo:admission:view`.

- [x] **Step 1: Write failing service and API tests**

Create records with these exact components:

```java
public record HistoricalCaseGroupView(String caseStatus,String caseType,long caseCount) { }

public record TodoHistoricalMigrationPreflightView(String gateCode,Instant generatedAt,
    long activeCaseCount,long deletedCaseCount,long historicalTodoCount,long orphanTodoVersionCount,
    long exceptionCandidateCount,List<HistoricalCaseGroupView> groups) { }
```

The service test must drive the desired API before it exists:

```java
when(mapper.selectHistoricalMigrationPreflightCounts()).thenReturn(Map.of(
    "active_case_count",12L,"deleted_case_count",2L,"historical_todo_count",7L,
    "orphan_todo_version_count",0L));
Map<String,Object> nullType=new HashMap<>();
nullType.put("case_status","pending");nullType.put("case_type",null);nullType.put("case_count",4L);
when(mapper.selectHistoricalCaseGroups()).thenReturn(List.of(
    Map.of("case_status","processing","case_type","litigation","case_count",8L),
    nullType));

var result=service.preflight("G-04");
assertEquals(12,result.activeCaseCount());
assertEquals(12,result.exceptionCandidateCount());
assertEquals("<NULL>",result.groups().get(1).caseType());
TodoException error=assertThrows(TodoException.class,()->service.preflight("G-05"));
assertEquals("TODO_MIGRATION_GATE_UNSUPPORTED",error.getBusinessCode());
verifyNoMoreInteractions(mapper);
```

The API test reflects the new method and asserts `@GetMapping("/preflight")` plus `@PreAuthorize` containing `todo:admission:view`.

- [x] **Step 2: Run RED**

```text
mvn -pl ruoyi-admin -am "-Dtest=TodoHistoricalMigrationPreflightServiceTest,HistoricalMigrationPreflightApiTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compilation fails because the records, service, Mapper methods and controller route do not exist.

- [x] **Step 3: Implement the minimal preflight path**

Implement the service with an injectable clock and exact gate validation:

```java
@Service
public class TodoHistoricalMigrationPreflightService {
  private final TodoHistoricalMigrationReadinessMapper mapper;
  private final Clock clock;
  public TodoHistoricalMigrationPreflightService(TodoHistoricalMigrationReadinessMapper mapper) {
    this(mapper,Clock.systemUTC());
  }
  TodoHistoricalMigrationPreflightService(TodoHistoricalMigrationReadinessMapper mapper,Clock clock) {
    this.mapper=mapper;this.clock=clock;
  }
  @Transactional(readOnly=true)
  public TodoHistoricalMigrationPreflightView preflight(String gateCode) {
    requireGate(gateCode);
    Map<String,Object> counts=mapper.selectHistoricalMigrationPreflightCounts();
    List<HistoricalCaseGroupView> groups=mapper.selectHistoricalCaseGroups().stream()
      .map(this::group).toList();
    long active=number(counts,"active_case_count");
    return new TodoHistoricalMigrationPreflightView("G-04",clock.instant(),active,
      number(counts,"deleted_case_count"),number(counts,"historical_todo_count"),
      number(counts,"orphan_todo_version_count"),active,List.copyOf(groups));
  }
  private static void requireGate(String value) {
    if(!"G-04".equals(value)) throw new TodoException("TODO_MIGRATION_GATE_UNSUPPORTED","Only G-04 is supported");
  }
  private static HistoricalCaseGroupView group(Map<String,Object> row) {
    return new HistoricalCaseGroupView(text(row,"case_status"),text(row,"case_type"),number(row,"case_count"));
  }
  private static long number(Map<String,Object> row,String key) {
    Object value=row.get(key);
    return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
  }
  private static String text(Map<String,Object> row,String key) {
    Object value=row.get(key);
    return value == null ? "<NULL>" : String.valueOf(value);
  }
}
```

Add exact read-only SQL:

```xml
<select id="selectHistoricalMigrationPreflightCounts" resultType="java.util.Map">
  select
    (select count(*) from biz_case where del_flag='0') active_case_count,
    (select count(*) from biz_case where del_flag&lt;&gt;'0') deleted_case_count,
    (select count(*) from todo_instance) historical_todo_count,
    (select count(*) from todo_instance i left join todo_template_version v
      on v.version_id=i.template_version_id where v.version_id is null) orphan_todo_version_count
</select>
<select id="selectHistoricalCaseGroups" resultType="java.util.Map">
  select coalesce(case_status,'&lt;NULL&gt;') case_status,
         coalesce(case_type,'&lt;NULL&gt;') case_type,count(*) case_count
  from biz_case where del_flag='0'
  group by coalesce(case_status,'&lt;NULL&gt;'),coalesce(case_type,'&lt;NULL&gt;')
  order by case_count desc,case_status,case_type
</select>
```

Inject the new service into `TodoHistoricalMigrationReadinessController` and return `AjaxResult.success(preflight.preflight(gateCode))` from `/preflight`.

- [x] **Step 4: Run GREEN**

Run Step 2. Expected: all new service/API tests pass.

- [x] **Step 5: Commit Task 1**

```text
git add law-todo/src/main/java/com/law/todo/application/view/HistoricalCaseGroupView.java law-todo/src/main/java/com/law/todo/application/view/TodoHistoricalMigrationPreflightView.java law-todo/src/main/java/com/law/todo/application/TodoHistoricalMigrationPreflightService.java law-todo/src/main/java/com/law/todo/mapper/TodoHistoricalMigrationReadinessMapper.java law-todo/src/main/resources/mapper/todo/TodoHistoricalMigrationReadinessMapper.xml ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoHistoricalMigrationReadinessController.java law-todo/src/test/java/com/law/todo/application/TodoHistoricalMigrationPreflightServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/web/todo/HistoricalMigrationPreflightApiTest.java
git commit -m "feat(migration): expose G04 historical preflight inventory"
```

---

### Task 2: Deterministic, bounded export archive writer

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/view/HistoricalMigrationCaseCandidate.java`
- Create: `law-todo/src/main/java/com/law/todo/application/view/HistoricalMigrationExportArtifact.java`
- Create: `law-todo/src/main/java/com/law/todo/application/HistoricalMigrationExportArchiveWriter.java`
- Create: `law-todo/src/test/java/com/law/todo/application/HistoricalMigrationExportArchiveWriterTest.java`

**Interfaces:**
- Consumes: `Iterator<HistoricalMigrationCaseCandidate>` in database order.
- Produces: `HistoricalMigrationExportArchiveWriter.write(Iterator<HistoricalMigrationCaseCandidate>): HistoricalMigrationExportArtifact`.
- `HistoricalMigrationExportArtifact` owns a cleanup-on-close `InputStream` and exposes `sizeBytes`, `rowCount`, `csvSha256`, `generatedAt`.

- [x] **Step 1: Write failing archive tests**

Define the desired records in the test imports:

```java
public record HistoricalMigrationCaseCandidate(Long caseId,String caseNo,String caseName,String caseType,
    String caseStatus,Long contractId,Long mainLawyerId,Long deptId) { }
public record HistoricalMigrationExportArtifact(InputStream input,long sizeBytes,long rowCount,
    String csvSha256,Instant generatedAt) implements AutoCloseable {
  @Override public void close() throws IOException { input.close(); }
}
```

Test two candidates, including `caseName="=HYPERLINK(\"x\")"`, a quote and a newline. Unzip the returned bytes and assert:

```java
assertEquals(2,artifact.rowCount());
assertTrue(csv.startsWith("\ufeff\"case_id\",\"case_no\""));
assertTrue(csv.contains("\"'=HYPERLINK(\"\"x\"\")\""));
assertTrue(csv.contains("\r\n"));
assertFalse(csv.contains("NON_LITIGATION"));
assertEquals(sha256(csvBytes),artifact.csvSha256());
assertEquals(artifact.csvSha256(),manifest.getString("csvSha256"));
assertEquals("UNREVIEWED",manifest.getString("classificationState"));
assertEquals(List.of("NON_LITIGATION","COMPREHENSIVE","EXECUTION"),manifest.getList("allowedBusinessLines",String.class));
```

After `artifact.close()`, assert the dedicated temporary root has no regular files. Add a failure test whose iterator throws after one row and assert the root is also empty and the business code is `TODO_MIGRATION_EXPORT_FAILED`.
Add a 100,000-row generated iterator that counts each `next()` call and never stores source rows; assert the writer consumes it once, reports 100,000 rows, produces a readable ZIP, and removes both temporary files after close.

- [x] **Step 2: Run RED**

```text
mvn -pl law-todo -am "-Dtest=HistoricalMigrationExportArchiveWriterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compilation fails because the candidate, artifact and writer are absent.

- [x] **Step 3: Implement deterministic CSV, manifest and cleanup**

Use these exact CSV columns and constants:

```java
private static final List<String> COLUMNS=List.of("case_id","case_no","case_name","case_type","case_status",
  "contract_id","main_lawyer_id","dept_id","proposed_business_line","exception_reason","review_status",
  "reviewer_user_id","reviewed_at");
private static final byte[] BOM={(byte)0xEF,(byte)0xBB,(byte)0xBF};
private static final List<String> ALLOWED_LINES=List.of("NON_LITIGATION","COMPREHENSIVE","EXECUTION");
```

The writer has a Spring constructor and a package-private test constructor:

```java
public HistoricalMigrationExportArchiveWriter(
    @Value("${todo.migration-export.temp-dir:${java.io.tmpdir}/law-todo-migration-export}") String root) {
  this(Path.of(root),Clock.systemUTC());
}
HistoricalMigrationExportArchiveWriter(Path root,Clock clock) { this.root=root.toAbsolutePath().normalize();this.clock=clock; }
```

For every cell, convert null to empty text, prefix a single quote when the first character is one of `= + - @ tab CR`, replace `"` with `""`, surround the value with double quotes, join with comma and terminate each row with CRLF. Write BOM and header first. Candidate rows must contain empty proposed/reviewer/reviewed fields, `NO_REVIEWED_CLASSIFICATION`, and `PENDING`.

Call `Files.createDirectories(root)`, create both files with `Files.createTempFile(root,...)`, and reject any normalized path that does not start with the normalized dedicated root. Generate the CSV in one temporary file while updating `MessageDigest.getInstance("SHA-256")`; then create a second temporary ZIP with these entries:

```text
historical-case-exceptions.csv
manifest.json
```

Serialize a `LinkedHashMap` manifest with `JSON.toJSONBytes`. Delete the CSV immediately after the ZIP is complete. Return a `FilterInputStream` whose idempotent `close()` deletes the ZIP. On every exception, delete both temporary files and throw:

```java
new TodoException("TODO_MIGRATION_EXPORT_FAILED","Historical migration export could not be generated")
```

- [x] **Step 4: Run GREEN and repeat determinism**

Run Step 2 twice. Expected: both runs pass; the CSV hashes asserted by the test are identical.

- [x] **Step 5: Commit Task 2**

```text
git add law-todo/src/main/java/com/law/todo/application/view/HistoricalMigrationCaseCandidate.java law-todo/src/main/java/com/law/todo/application/view/HistoricalMigrationExportArtifact.java law-todo/src/main/java/com/law/todo/application/HistoricalMigrationExportArchiveWriter.java law-todo/src/test/java/com/law/todo/application/HistoricalMigrationExportArchiveWriterTest.java
git commit -m "feat(migration): create deterministic G04 evidence archive"
```

---

### Task 3: Cursor export service, controlled HTTP response and permission

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoHistoricalMigrationExportService.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoHistoricalMigrationReadinessMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoHistoricalMigrationReadinessMapper.xml`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoHistoricalMigrationReadinessController.java`
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_27__foundation_g04_preflight_export_permission.sql`
- Create: `law-todo/src/test/java/com/law/todo/application/TodoHistoricalMigrationExportServiceTest.java`
- Create: `law-todo/src/test/java/com/law/todo/integration/HistoricalMigrationPreflightPermissionContractTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/todo/HistoricalMigrationPreflightApiTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/FlywayMigrationTest.java`

**Interfaces:**
- Mapper produces: `Cursor<Map<String,Object>> streamHistoricalCaseCandidates()` ordered by `case_id`.
- Service produces: `TodoHistoricalMigrationExportService.export(String): HistoricalMigrationExportArtifact` under `REPEATABLE_READ` read-only transaction.
- HTTP produces: `GET /todo/foundation-migration/exception-export?gateCode=G-04` protected by `todo:admission:export`.

- [x] **Step 1: Write failing cursor, controller and migration contract tests**

In the service test, mock a MyBatis `Cursor<Map<String,Object>>`, make its iterator return two maps, and assert the writer receives an iterator whose candidates retain all eight source fields. Return `active_case_count=2` from `selectHistoricalMigrationPreflightCounts()` and require it to equal `artifact.rowCount()`. Verify the cursor and mismatched artifact close after success, writer failure and count mismatch.

Extend the API test to assert:

```java
assertPermission("exceptionExport","todo:admission:export");
assertGetMapping("exceptionExport","/exception-export");
assertEquals("application/zip",response.getHeaders().getContentType().toString());
assertEquals("attachment; filename=\"g04-historical-case-preflight.zip\"",response.getHeaders().getFirst("Content-Disposition"));
assertEquals("2",response.getHeaders().getFirst("X-Exception-Row-Count"));
assertEquals(SHA,response.getHeaders().getFirst("X-Exception-CSV-SHA256"));
assertEquals("no-store",response.getHeaders().getCacheControl());
assertEquals("nosniff",response.getHeaders().getFirst("X-Content-Type-Options"));
```

Execute the returned `StreamingResponseBody` once with a successful output stream and once with an output stream that throws on write; in both cases verify `HistoricalMigrationExportArtifact.close()` is called so client disconnects cannot retain the ZIP.

The migration contract reads `V0_20_27__foundation_g04_preflight_export_permission.sql` and asserts one `todo:admission:export`, no `sys_role_menu`, and absence of `alter table biz_case`, `update biz_case`, `update todo_instance`, `todo_foundation_migration_requirement`, `todo_admission_evidence`, `approved`, `confirmed`.

- [x] **Step 2: Run RED**

```text
mvn -pl ruoyi-admin -am "-Dtest=TodoHistoricalMigrationExportServiceTest,HistoricalMigrationPreflightPermissionContractTest,HistoricalMigrationPreflightApiTest,FlywayMigrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compilation and contract failures because the cursor service, endpoint and migration are absent and Flyway still ends at `0.20.26`.

- [x] **Step 3: Implement cursor mapping and export response**

Add this Mapper method and query:

```java
Cursor<Map<String,Object>> streamHistoricalCaseCandidates();
```

```xml
<select id="streamHistoricalCaseCandidates" resultType="java.util.Map" fetchSize="-2147483648">
  select case_id,case_no,case_name,case_type,case_status,contract_id,main_lawyer_id,dept_id
  from biz_case where del_flag='0' order by case_id
</select>
```

Implement the service transaction exactly as a synchronous cursor-to-writer handoff:

```java
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public HistoricalMigrationExportArtifact export(String gateCode) {
  requireGate(gateCode);
  long expected=number(mapper.selectHistoricalMigrationPreflightCounts(),"active_case_count");
  try(Cursor<Map<String,Object>> cursor=mapper.streamHistoricalCaseCandidates()) {
    Iterator<Map<String,Object>> rows=cursor.iterator();
    Iterator<HistoricalMigrationCaseCandidate> candidates=new Iterator<>() {
      @Override public boolean hasNext() { return rows.hasNext(); }
      @Override public HistoricalMigrationCaseCandidate next() { return candidate(rows.next()); }
    };
    HistoricalMigrationExportArtifact artifact=writer.write(candidates);
    if(artifact.rowCount()!=expected) {
      artifact.close();
      throw new TodoException("TODO_MIGRATION_EXPORT_FAILED","Historical migration export could not be generated");
    }
    return artifact;
  } catch(TodoException error) { throw error; }
  catch(Exception error) { throw new TodoException("TODO_MIGRATION_EXPORT_FAILED","Historical migration export could not be generated"); }
}
private static HistoricalMigrationCaseCandidate candidate(Map<String,Object> row) {
  return new HistoricalMigrationCaseCandidate(nullableLong(row,"case_id"),text(row,"case_no"),
    text(row,"case_name"),text(row,"case_type"),text(row,"case_status"),
    nullableLong(row,"contract_id"),nullableLong(row,"main_lawyer_id"),nullableLong(row,"dept_id"));
}
private static Long nullableLong(Map<String,Object> row,String key) {
  Object value=row.get(key);
  if(value == null) return null;
  return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
}
private static String text(Map<String,Object> row,String key) {
  Object value=row.get(key);
  return value == null ? "" : String.valueOf(value);
}
private static long number(Map<String,Object> row,String key) {
  Object value=row.get(key);
  return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
}
private static void requireGate(String value) {
  if(!"G-04".equals(value)) throw new TodoException("TODO_MIGRATION_GATE_UNSUPPORTED","Only G-04 is supported");
}
```

The controller creates a `StreamingResponseBody` that closes the artifact in a try-with-resources block and copies `artifact.input()` to the response. Set `Content-Type: application/zip`, `Content-Disposition: attachment; filename="g04-historical-case-preflight.zip"`, `X-Exception-Row-Count`, `X-Exception-CSV-SHA256`, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, and `Content-Length` from the artifact; then annotate with:

```java
@PreAuthorize("@ss.hasPermi('todo:admission:export')")
@Log(title="G-04历史迁移异常清单",businessType=BusinessType.EXPORT)
@GetMapping("/exception-export")
```

Create the forward migration:

```sql
set @definition_menu=(select menu_id from sys_menu where component='todo/config/index' order by menu_id limit 1);
set @fallback_menu=(select menu_id from sys_menu where perms='todo:list' order by menu_id limit 1);
set @permission_parent=coalesce(@definition_menu,@fallback_menu);
insert into sys_menu(menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,
  menu_type,visible,status,perms,icon,create_by,create_time)
select 'Historical migration evidence export',@permission_parent,48,'#','',null,null,1,0,'F','0','0',
  'todo:admission:export','#','admin',sysdate()
where @permission_parent is not null
  and not exists(select 1 from sys_menu where perms='todo:admission:export');
```

Update `FlywayMigrationTest` terminal version to `0.20.27` and assert exactly one permission row with no automatic role grant.

- [x] **Step 4: Run GREEN and law-todo regression**

```text
mvn -pl ruoyi-admin -am "-Dtest=TodoHistoricalMigrationExportServiceTest,HistoricalMigrationPreflightPermissionContractTest,HistoricalMigrationPreflightApiTest,FlywayMigrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -pl law-todo -am test
```

Expected: all focused tests and the complete `law-todo` suite pass.

- [x] **Step 5: Commit Task 3**

```text
git add law-todo/src/main/java/com/law/todo/application/TodoHistoricalMigrationExportService.java law-todo/src/main/java/com/law/todo/mapper/TodoHistoricalMigrationReadinessMapper.java law-todo/src/main/resources/mapper/todo/TodoHistoricalMigrationReadinessMapper.xml ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoHistoricalMigrationReadinessController.java ruoyi-admin/src/main/resources/db/migration/V0_20_27__foundation_g04_preflight_export_permission.sql law-todo/src/test/java/com/law/todo/application/TodoHistoricalMigrationExportServiceTest.java law-todo/src/test/java/com/law/todo/integration/HistoricalMigrationPreflightPermissionContractTest.java ruoyi-admin/src/test/java/com/ruoyi/web/todo/HistoricalMigrationPreflightApiTest.java ruoyi-admin/src/test/java/com/ruoyi/web/migration/FlywayMigrationTest.java
git commit -m "feat(migration): secure G04 exception evidence export"
```

---

### Task 4: Frontend preflight and evidence download

**Files:**
- Modify: `ruoyi-ui/src/utils/request.js`
- Modify: `ruoyi-ui/src/api/todo-definition.js`
- Modify: `ruoyi-ui/src/views/todo/config/components/HistoricalMigrationReadiness.vue`
- Modify: `ruoyi-ui/scripts/check-todo-ui.js`
- Modify: `ruoyi-ui/e2e/todo-foundation-config-resources.spec.js`

**Interfaces:**
- API produces: `getHistoricalMigrationPreflight('G-04')` and `exportHistoricalMigrationExceptions('G-04')`.
- Binary request opt-in: `returnFullResponse: true` returns the Axios response only for blob/arraybuffer requests.
- UI displays preflight data independently of the existing `report` prop and displays last export row count/hash.

- [x] **Step 1: Write failing frontend contract and Playwright expectations**

Extend `check-todo-ui.js` to require these exact API names and component markers:

```text
getHistoricalMigrationPreflight
exportHistoricalMigrationExceptions
todo:admission:export
exceptionCandidateCount
csvSha256
UNREVIEWED
```

Add mocked routes for `/todo/foundation-migration/preflight` and `/todo/foundation-migration/exception-export`. The export route returns a ZIP Blob with:

```text
X-Exception-Row-Count: 12
X-Exception-CSV-SHA256: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
```

Extend the historical migration E2E to assert grouped inventory, candidate count, warning text, a browser download named `g04-historical-case-preflight.zip`, and visible row count/hash after download. Add a second test where preflight returns HTTP 500 and assert the existing requirements table remains visible while only the local preflight alert reports failure.
Add a third test whose mocked user lacks `todo:admission:export`; assert the inventory remains visible and the export button is absent.

- [x] **Step 2: Run RED**

```text
cd ruoyi-ui
npm run test:todo
npx playwright test e2e/todo-foundation-config-resources.spec.js --grep "historical migration"
```

Expected: the contract fails on missing APIs/markers and Playwright fails on missing preflight UI/export.

- [x] **Step 3: Implement isolated binary metadata and component state**

Change the binary branch in `request.js` without affecting existing callers:

```javascript
if (res.request.responseType === 'blob' || res.request.responseType === 'arraybuffer') {
  return res.config.returnFullResponse ? res : res.data
}
```

Add APIs:

```javascript
export function getHistoricalMigrationPreflight(gateCode = 'G-04') {
  return request({ url: '/todo/foundation-migration/preflight', method: 'get', params: { gateCode } })
}
export function exportHistoricalMigrationExceptions(gateCode = 'G-04') {
  return request({ url: '/todo/foundation-migration/exception-export', method: 'get', params: { gateCode },
    responseType: 'blob', returnFullResponse: true }).then(response => ({
      blob: response.data,
      rowCount: Number(response.headers['x-exception-row-count'] || 0),
      csvSha256: response.headers['x-exception-csv-sha256'] || ''
    }))
}
```

In `HistoricalMigrationReadiness.vue`, keep the existing `report` prop and add local state:

```javascript
data() { return { preflight: { groups: [] }, preflightLoading: false, preflightError: '', exporting: false, lastExport: null } },
mounted() { this.loadPreflight() },
methods: {
  loadPreflight() {
    this.preflightLoading = true; this.preflightError = ''
    return getHistoricalMigrationPreflight().then(response => { this.preflight = response.data || { groups: [] } })
      .catch(() => { this.preflightError = '历史数据预检加载失败，原准入目录未受影响' })
      .finally(() => { this.preflightLoading = false })
  },
  exportExceptions() {
    this.exporting = true
    return exportHistoricalMigrationExceptions().then(result => {
      saveAs(result.blob,'g04-historical-case-preflight.zip')
      this.lastExport = { rowCount: result.rowCount, csvSha256: result.csvSha256, classificationState: 'UNREVIEWED' }
    }).finally(() => { this.exporting = false })
  }
}
```

Add summary cards and the grouped table, the `v-hasPermi="['todo:admission:export']"` button, last-export metadata, and the exact warning “清单尚未分类、尚未签字，不会自动改变 G-04”。 Do not change the existing readiness table or gate banner.

- [x] **Step 4: Run GREEN and frontend gates**

```text
cd ruoyi-ui
npm run test:todo
npm run test:todo-schema
npm run test:encoding
npm run test:foundation-ci
npm run build:prod
npx playwright test e2e/todo-foundation-config-resources.spec.js --grep "historical migration"
```

Expected: contracts/build pass and both historical migration Playwright scenarios pass.

- [x] **Step 5: Commit Task 4**

```text
git add ruoyi-ui/src/utils/request.js ruoyi-ui/src/api/todo-definition.js ruoyi-ui/src/views/todo/config/components/HistoricalMigrationReadiness.vue ruoyi-ui/scripts/check-todo-ui.js ruoyi-ui/e2e/todo-foundation-config-resources.spec.js
git commit -m "feat(migration): show and export G04 preflight evidence"
```

---

### Task 5: Real MySQL immutability evidence and truthful report

**Files:**
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/HistoricalMigrationPreflightEndToEndTest.java`
- Modify: `doc/reviews/v0.2-foundation-g04-historical-migration-review-package.md`
- Modify: `doc/v0.2-foundation-admission-report.md`
- Modify: this plan file

**Interfaces:**
- Consumes: fresh MySQL 8.4 from the exact v0.15 baseline, full Flyway chain through `0.20.27`, real MyBatis cursor and real archive writer.
- Produces: measured evidence that preflight/export is accurate and does not mutate historical cases, Todo instances, G-04 sources or admission evidence.

- [x] **Step 1: Write the real-database E2E before running it**

Create `HistoricalMigrationPreflightEndToEndTest` using `TODO_MIGRATION_DB_URL/USER/PASSWORD`, Flyway, `SqlSession`, real `TodoHistoricalMigrationReadinessMapper`, fixed clock and `@TempDir`. Insert active/deleted case fixtures covering nulls, Chinese, quotes, newline and formula prefix. Before export, capture complete ordered Java row snapshots from `biz_case`, `todo_instance`, all eight G-04 requirements, `G04-HISTORICAL-MIGRATION`, and Q-001. Also capture this runtime invariant:

```sql
select count(*) from todo_instance i left join todo_template_version v
  on v.version_id=i.template_version_id where v.version_id is null;
```

Use deterministic `select * ... order by` statements for every snapshot and require exact before/after `List<Map<String,Object>>` equality. Call real preflight/export, unzip and validate CSV/Manifest/hash, then repeat all snapshots and the orphan invariant. Assert `biz_case.business_line` remains absent, G-04 still has exactly three `CONFIRMED`, one `NEEDS_DECISION` and four `NEEDS_EVIDENCE` sources (five unresolved total), its evidence is still `OPEN`, Q-001 is still `OPEN`, the existing Foundation readiness query still reports `NOT_ADMITTED`, and the artifact root is empty after close.

- [x] **Step 2: Run against a disposable MySQL 8.4**

Create and initialize the disposable database with this exact PowerShell sequence (the fixed container name is reused in Step 5):

```powershell
docker run --name foundation-g04-preflight-20260718 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=law_v017 -p 13317:3306 --health-cmd="mysqladmin ping -h localhost -proot" --health-interval=2s --health-timeout=2s --health-retries=30 -d mysql:8.4
while ((docker inspect --format='{{.State.Health.Status}}' foundation-g04-preflight-20260718) -ne 'healthy') { Start-Sleep -Seconds 2 }
$baseline=@(
  'sql/ry_20260417.sql','sql/quartz.sql','sql/lead_module_20260602.sql','sql/lead_menu_20260602.sql',
  'sql/customer_contract_module_20260603.sql','sql/customer_contract_dict_patch_20260611.sql',
  'sql/case_module_20260611.sql','sql/matter_module_20260615.sql','sql/matter_menu_patch_20260617.sql',
  'sql/finance_module_20260624.sql','sql/customer_tag_assign_permission_fix_20260627.sql'
)
foreach($file in $baseline) { Get-Content -Raw $file | docker exec -i foundation-g04-preflight-20260718 mysql -uroot -proot --default-character-set=utf8mb4 law_v017 }
$env:TODO_MIGRATION_DB_URL='jdbc:mysql://127.0.0.1:13317/law_v017?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
$env:TODO_MIGRATION_DB_USER='root'
$env:TODO_MIGRATION_DB_PASSWORD='root'
```

Then run:

```text
mvn -pl ruoyi-admin -am "-Dtest=HistoricalMigrationPreflightEndToEndTest,FlywayMigrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: Flyway reaches `0.20.27`; both tests pass with zero skips; business data and governance state checks remain unchanged.

- [x] **Step 3: Update review package and admission report from evidence**

Update the G-04 review package to cite the preflight API, deterministic CSV/Manifest, permission, formula protection, temp cleanup and real MySQL immutability test. Keep `PENDING_ARCHITECTURE_CASE_DBA_REVIEW`, default business line undecided, four sources `NEEDS_EVIDENCE`, evidence `OPEN`, and G-04 not ready.

Update the admission report with terminal Flyway `0.20.27`, fresh Maven module totals, new preflight/export front/backend capability, unchanged `2/8 NOT_ADMITTED`, 12 OPEN decisions and five OPEN evidence items.

- [x] **Step 4: Run full verification**

Against a fresh disposable MySQL 8.4:

```text
mvn clean verify
cd ruoyi-ui
npm run test:todo
npm run test:todo-schema
npm run test:encoding
npm run test:foundation-ci
npm run build:prod
npm run test:e2e
```

Parse all Surefire XML and require zero failures, errors and skips. Require every Playwright test to pass. Query live truth: Flyway `0.20.27`, G-04 source counts unchanged, G-05 still 6/7, five evidence items OPEN, twelve decisions OPEN, aggregate `2/8 NOT_ADMITTED`.

- [x] **Step 5: Audit, remove the database and commit locally**

```text
git diff --check
git status --short
docker rm -f foundation-g04-preflight-20260718
git add ruoyi-admin/src/test/java/com/ruoyi/web/migration/HistoricalMigrationPreflightEndToEndTest.java doc/reviews/v0.2-foundation-g04-historical-migration-review-package.md doc/v0.2-foundation-admission-report.md docs/superpowers/plans/2026-07-18-g04-historical-migration-preflight.md
git commit -m "test(migration): record G04 preflight evidence"
```

Do not push. This slice must end with G-04 still blocked by Q-001, signed exception classification, architecture/case/DBA rehearsal evidence and independent approval.
