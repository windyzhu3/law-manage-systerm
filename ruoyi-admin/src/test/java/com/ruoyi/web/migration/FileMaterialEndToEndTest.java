package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.law.file.application.FileObjectService;
import com.law.file.application.FileObjectService.AccessTokenView;
import com.law.file.application.FileObjectService.UploadIntentView;
import com.law.file.domain.FileException;
import com.law.file.domain.FileObject.AccessLog;
import com.law.file.infrastructure.FileCleanupAuditAdapter;
import com.law.file.infrastructure.LocalFileStorageAdapter;
import com.law.file.infrastructure.MyBatisFileObjectRepository;
import com.law.file.mapper.FileObjectMapper;
import com.law.file.security.FileAccessPolicy;
import com.law.file.security.FileContentPolicy;
import com.law.file.spi.FileBusinessAccessChecker;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.web.controller.file.FileObjectController;
import com.ruoyi.web.controller.file.FileObjectController.RegisterUploadRequest;
import com.ruoyi.web.controller.file.FileObjectController.VersionView;

class FileMaterialEndToEndTest
{
    private static final long ACTOR_ID=7L;
    private static final long DEPT_ID=3L;

    @Test
    void every_prd_material_type_uploads_previews_downloads_and_persists_access_audit(@TempDir Path storageRoot)
        throws Exception
    {
        String url=System.getenv("TODO_MIGRATION_DB_URL");
        assumeTrue(url!=null&&!url.isBlank(),"Migration database is provided by the CI quality gate");
        String user=System.getenv("TODO_MIGRATION_DB_USER"),password=System.getenv("TODO_MIGRATION_DB_PASSWORD");
        Flyway.configure().dataSource(url,user,password).baselineOnMigrate(true).baselineVersion("0.15.0")
            .locations("classpath:db/migration").load().migrate();

        DataSource dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,user,password);
        List<MaterialDefinition> materials=prdMaterials(dataSource);
        assertEquals(21,materials.size(),"The reviewed PRD material catalogue must not drift silently");
        assertTrue(materials.stream().anyMatch(value->"CONTACT_PROOF".equals(value.materialType())));
        assertTrue(materials.stream().anyMatch(value->"ARCHIVE_SCAN".equals(value.materialType())));

        Configuration configuration=new Configuration(new Environment("file-material-e2e",
            new JdbcTransactionFactory(),dataSource));
        String mapperResource="mapper/file/FileObjectMapper.xml";
        try(InputStream input=Resources.getResourceAsStream(mapperResource))
        {new XMLMapperBuilder(input,configuration,mapperResource,configuration.getSqlFragments()).parse();}

        try(SqlSession session=new SqlSessionFactoryBuilder().build(configuration).openSession(true);
            var security=mockStatic(SecurityUtils.class);var ip=mockStatic(IpUtils.class))
        {
            MyBatisFileObjectRepository repository=new MyBatisFileObjectRepository(session.getMapper(FileObjectMapper.class));
            FileBusinessAccessChecker checker=new FileBusinessAccessChecker()
            {
                @Override public boolean supports(String businessType){return businessType!=null&&!businessType.isBlank();}
                @Override public boolean canRead(String businessType,Long businessId,Long userId,Long deptId)
                {return businessId!=null&&businessId>0&&ACTOR_ID==userId&&DEPT_ID==deptId;}
            };
            FileAccessPolicy access=new FileAccessPolicy(repository,List.of(checker));
            FileObjectService service=new FileObjectService(repository,new LocalFileStorageAdapter(storageRoot),access,
                new FileCleanupAuditAdapter(repository),new FileContentPolicy());
            FileObjectController controller=new FileObjectController(service);
            security.when(SecurityUtils::getUserId).thenReturn(ACTOR_ID);
            security.when(SecurityUtils::getUsername).thenReturn("foundation-file-e2e");
            security.when(SecurityUtils::getDeptId).thenReturn(DEPT_ID);
            ip.when(IpUtils::getIpAddr).thenReturn("127.0.0.1");

            int sequence=0;
            for(MaterialDefinition material:materials)
            {
                sequence++;
                byte[] content=("v0.2-material:"+material.materialType()).getBytes(StandardCharsets.UTF_8);
                String fileName="material-"+sequence+".txt";
                RegisterUploadRequest request=new RegisterUploadRequest(UUID.randomUUID().toString(),fileName,
                    "text/plain",content.length,sha256(content),material.businessType(),900000L+sequence,
                    material.materialType(),"BUSINESS");
                UploadIntentView intent=(UploadIntentView)controller.register(request).get("data");
                VersionView version=(VersionView)controller.complete(intent.uploadIntentId(),
                    new MockMultipartFile("file",fileName,"text/plain",content)).get("data");
                var relation=repository.findActiveRelations(version.fileObjectId()).stream()
                    .filter(value->material.materialType().equals(value.materialType())).findFirst().orElseThrow();

                AccessTokenView preview=(AccessTokenView)controller.previewToken(version.fileObjectId(),relation.relationId()).get("data");
                var previewResponse=controller.open(preview.token());
                assertTrue(previewResponse.getHeaders().getFirst("Content-Disposition").startsWith("inline"));
                assertEquals("nosniff",previewResponse.getHeaders().getFirst("X-Content-Type-Options"));
                assertEquals("sandbox; default-src 'none'",previewResponse.getHeaders().getFirst("Content-Security-Policy"));
                assertEquals("no-referrer",previewResponse.getHeaders().getFirst("Referrer-Policy"));
                ByteArrayOutputStream previewBytes=new ByteArrayOutputStream();
                previewResponse.getBody().writeTo(previewBytes);
                assertArrayEquals(content,previewBytes.toByteArray());
                assertThrows(FileException.class,()->controller.open(preview.token()));

                AccessTokenView download=(AccessTokenView)controller.downloadToken(version.fileObjectId(),relation.relationId()).get("data");
                var downloadResponse=controller.open(download.token());
                assertTrue(downloadResponse.getHeaders().getFirst("Content-Disposition").startsWith("attachment"));
                assertEquals("nosniff",downloadResponse.getHeaders().getFirst("X-Content-Type-Options"));
                assertEquals("sandbox; default-src 'none'",downloadResponse.getHeaders().getFirst("Content-Security-Policy"));
                assertEquals("no-referrer",downloadResponse.getHeaders().getFirst("Referrer-Policy"));
                ByteArrayOutputStream downloadBytes=new ByteArrayOutputStream();
                downloadResponse.getBody().writeTo(downloadBytes);
                assertArrayEquals(content,downloadBytes.toByteArray());

                List<AccessLog> logs=repository.findAccessLogs(version.fileObjectId());
                assertEquals(4,logs.size());
                assertEquals(2,logs.stream().filter(value->"ACCESS_OPEN".equals(value.eventType())).count());
                assertEquals(1,logs.stream().filter(value->"PREVIEW_SUCCESS".equals(value.eventType())
                    &&"SUCCESS".equals(value.outcome())).count());
                assertEquals(1,logs.stream().filter(value->"DOWNLOAD_SUCCESS".equals(value.eventType())
                    &&"SUCCESS".equals(value.outcome())).count());
                assertTrue(logs.stream().allMatch(value->ACTOR_ID==value.actorId()&&DEPT_ID==value.actorDeptId()
                    &&"127.0.0.1".equals(value.clientIp())));
                assertEquals(4,((List<?>)controller.accessLogs(version.fileObjectId()).get("data")).size());
            }

            MaterialDefinition activeMaterial=materials.get(0);
            byte[] activeContent="<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
            RegisterUploadRequest activeRequest=new RegisterUploadRequest(UUID.randomUUID().toString(),"active.html",
                "text/html",activeContent.length,sha256(activeContent),activeMaterial.businessType(),990000L,
                activeMaterial.materialType(),"BUSINESS");
            UploadIntentView activeIntent=(UploadIntentView)controller.register(activeRequest).get("data");
            VersionView activeVersion=(VersionView)controller.complete(activeIntent.uploadIntentId(),
                new MockMultipartFile("file","active.html","text/html",activeContent)).get("data");
            var activeRelation=repository.findActiveRelations(activeVersion.fileObjectId()).get(0);
            AccessTokenView activeToken=(AccessTokenView)controller.previewToken(activeVersion.fileObjectId(),
                activeRelation.relationId()).get("data");
            var activeResponse=controller.open(activeToken.token());
            assertEquals(MediaType.APPLICATION_OCTET_STREAM,activeResponse.getHeaders().getContentType());
            assertTrue(activeResponse.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("attachment"));
            assertEquals("nosniff",activeResponse.getHeaders().getFirst("X-Content-Type-Options"));
            assertEquals("sandbox; default-src 'none'",activeResponse.getHeaders().getFirst("Content-Security-Policy"));
            assertEquals("no-referrer",activeResponse.getHeaders().getFirst("Referrer-Policy"));
            ByteArrayOutputStream activeBytes=new ByteArrayOutputStream();
            activeResponse.getBody().writeTo(activeBytes);
            assertArrayEquals(activeContent,activeBytes.toByteArray());
            assertThrows(FileException.class,()->controller.open(activeToken.token()));
            assertEquals(2,repository.findAccessLogs(activeVersion.fileObjectId()).size());
        }
    }

    private static List<MaterialDefinition> prdMaterials(DataSource dataSource) throws Exception
    {
        String sql="select distinct c.business_type,m.material_type from todo_prd_definition_catalog c "
            +"join json_table(c.definition_json,'$.dod.config.materials[*]' "
            +"columns(material_type varchar(96) path '$.type')) m "
            +"where m.material_type is not null and length(trim(m.material_type))>0 "
            +"order by c.business_type,m.material_type";
        List<MaterialDefinition> result=new ArrayList<>();
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement();
            ResultSet rows=statement.executeQuery(sql))
        {while(rows.next())result.add(new MaterialDefinition(rows.getString(1),rows.getString(2)));}
        return List.copyOf(result);
    }

    private static String sha256(byte[] value) throws Exception
    {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}

    private record MaterialDefinition(String businessType,String materialType) { }
}
