package com.ruoyi.web.migration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.law.file.application.FileObjectService;
import com.law.file.application.FileObjectService.RetireFileObjectCommand;
import com.law.file.application.FileObjectService.RetireFileObjectView;
import com.law.file.domain.FileObject.FileActor;
import com.law.file.infrastructure.MyBatisFileObjectRepository;
import com.law.file.mapper.FileObjectMapper;
import com.law.file.repository.FileObjectRepository;
import com.law.file.security.DetectedContentType;
import com.law.file.security.FileAccessPolicy;
import com.law.file.security.FileContentPolicy;
import com.law.file.spi.FileBusinessAccessChecker;
import com.law.file.spi.FileCleanupAuditPort;
import com.law.file.spi.FileStoragePort;

/**
 * Real InnoDB proof for the canonical file-object lock order. Mock-based
 * concurrency tests cannot prove that attach and retirement serialize on the
 * same database row.
 */
class FileObjectLockOrderExternalMysqlIT
{
    private static final FileActor OWNER=new FileActor(7L,"owner",3L);

    @Test
    void differentRelationsRetiringConcurrentlyLeaveNoActiveOrphanAndOneDurableCleanupSet() throws Exception
    {
        try(Harness harness=Harness.create("two_retire"))
        {
            harness.fixture(2);
            CyclicBarrier bothReadTwoRelations=new CyclicBarrier(2);
            ThreadLocal<Integer> reads=ThreadLocal.withInitial(()->0);
            ThreadLocal<Boolean> objectLocked=ThreadLocal.withInitial(()->false);
            FileObjectMapper mapper=harness.mapper((method,proceed)->{
                if("selectObjectForUpdate".equals(method.getName()))
                {
                    Object result=proceed.call();
                    objectLocked.set(true);
                    return result;
                }
                Object result=proceed.call();
                if(method.getName().startsWith("selectActiveRelations")&&!objectLocked.get()
                    &&reads.get()+1==2)
                    bothReadTwoRelations.await(10,SECONDS);
                if(method.getName().startsWith("selectActiveRelations"))reads.set(reads.get()+1);
                return result;
            });
            FileObjectService service=harness.service(mapper);
            ExecutorService workers=Executors.newFixedThreadPool(2);
            try
            {
                Future<RetireFileObjectView> first=workers.submit(()->harness.tx(()->service.retire(
                    1L,new RetireFileObjectCommand("retire-r1",11L,true),OWNER)));
                Future<RetireFileObjectView> second=workers.submit(()->harness.tx(()->service.retire(
                    1L,new RetireFileObjectCommand("retire-r2",12L,true),OWNER)));

                List<RetireFileObjectView> results=List.of(first.get(15,SECONDS),second.get(15,SECONDS));
                assertEquals(1,results.stream().filter(RetireFileObjectView::objectRetired).count());
            }
            finally
            {
                workers.shutdownNow();
                workers.awaitTermination(10,SECONDS);
            }

            harness.assertNoBrokenObjectRelationState();
            assertEquals("DISABLED",harness.text("select status from file_object where file_object_id=1"));
            assertEquals(0,harness.count("select count(*) from file_business_relation where file_object_id=1 and active=1"));
            harness.assertOneCleanupSet(2,2);
            assertEquals(2,harness.count("select count(*) from file_lifecycle_audit where event_type='RELATION_RETIRED'"));
            assertEquals(1,harness.count("select count(*) from file_lifecycle_audit where event_type='OBJECT_RETIRED'"));
        }
    }

    @Test
    void finalRetirementWinningAgainstAttachRejectsTheLateAttachAndNeverLeavesDisabledAuthority() throws Exception
    {
        try(Harness harness=Harness.create("retire_attach"))
        {
            harness.fixture(1);
            CountDownLatch retireReadFinalRelation=new CountDownLatch(1);
            CountDownLatch attachStarted=new CountDownLatch(1);
            CountDownLatch retireCommitted=new CountDownLatch(1);
            ThreadLocal<Integer> reads=ThreadLocal.withInitial(()->0);
            ThreadLocal<Boolean> objectLocked=ThreadLocal.withInitial(()->false);
            FileObjectMapper mapper=harness.mapper((method,proceed)->{
                if("selectObjectForUpdate".equals(method.getName()))
                {
                    Object result=proceed.call();
                    objectLocked.set(true);
                    return result;
                }
                if("insertRelation".equals(method.getName())&&!objectLocked.get())
                    assertTrue(retireCommitted.await(10,SECONDS));
                Object result=proceed.call();
                if(method.getName().startsWith("selectActiveRelations"))
                {
                    reads.set(reads.get()+1);
                    if(Thread.currentThread().getName().contains("retire-winner")&&reads.get()==2)
                    {
                        retireReadFinalRelation.countDown();
                        assertTrue(attachStarted.await(10,SECONDS));
                    }
                }
                return result;
            });
            FileObjectService service=harness.service(mapper);
            ExecutorService workers=Executors.newFixedThreadPool(2,runnable->{
                Thread thread=new Thread(runnable);
                thread.setDaemon(true);
                thread.setName("file-lock-worker");
                return thread;
            });
            try
            {
                Future<RetireFileObjectView> retirement=workers.submit(()->{
                    Thread.currentThread().setName("retire-winner");
                    try
                    {
                        return harness.tx(()->service.retire(
                            1L,new RetireFileObjectCommand("retire-final",11L,true),OWNER));
                    }
                    finally
                    {
                        retireCommitted.countDown();
                    }
                });
                assertTrue(retireReadFinalRelation.await(10,SECONDS));
                Future<Boolean> attach=workers.submit(()->{
                    Thread.currentThread().setName("late-attach");
                    attachStarted.countDown();
                    try
                    {
                        harness.tx(()->service.relate(
                            1L,"late-attach-action","LEAD",9002L,"CONTACT_PROOF","BUSINESS",OWNER));
                        return true;
                    }
                    catch(RuntimeException rejected)
                    {
                        return false;
                    }
                });

                assertTrue(retirement.get(15,SECONDS).objectRetired());
                assertFalse(attach.get(15,SECONDS),"an attach serialized behind final retirement must be rejected");
            }
            finally
            {
                retireCommitted.countDown();
                workers.shutdownNow();
                workers.awaitTermination(10,SECONDS);
            }

            harness.assertNoBrokenObjectRelationState();
            assertEquals("DISABLED",harness.text("select status from file_object where file_object_id=1"));
            assertEquals(0,harness.count("select count(*) from file_business_relation where file_object_id=1 and active=1"));
            assertEquals(0,harness.count("select count(*) from file_relation_action where action_id='late-attach-action'"));
            harness.assertOneCleanupSet(2,1);
        }
    }

    private interface Invocation
    {
        Object invoke(java.lang.reflect.Method method,CheckedCall proceed) throws Throwable;
    }

    private interface CheckedCall { Object call() throws Throwable; }
    private interface Work<T> { T run(); }

    private static final class Harness implements AutoCloseable
    {
        private final String adminUrl;
        private final String user;
        private final String password;
        private final String schema;
        private final DataSource dataSource;
        private final FileObjectMapper delegate;
        private final TransactionTemplate transaction;
        private final RecordingStorage storage=new RecordingStorage();
        private final RecordingCleanup cleanup=new RecordingCleanup();

        private Harness(String adminUrl,String user,String password,String schema,DataSource dataSource,
            FileObjectMapper delegate)
        {
            this.adminUrl=adminUrl;this.user=user;this.password=password;this.schema=schema;
            this.dataSource=dataSource;this.delegate=delegate;
            this.transaction=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        }

        static Harness create(String label) throws Exception
        {
            String adminUrl=required("TODO_MIGRATION_DB_URL");
            String user=required("TODO_MIGRATION_DB_USER");
            String password=required("TODO_MIGRATION_DB_PASSWORD");
            String schema="file_lock_"+label+"_"+UUID.randomUUID().toString().replace("-","");
            try(Connection connection=DriverManager.getConnection(adminUrl,user,password);
                Statement statement=connection.createStatement())
            {
                statement.execute("create database "+schema+" character set utf8mb4 collate utf8mb4_unicode_ci");
            }
            DataSource dataSource=new UnpooledDataSource(
                "com.mysql.cj.jdbc.Driver",withSchema(adminUrl,schema),user,password);
            createTables(dataSource);
            Configuration configuration=new Configuration(new Environment(
                "file-lock-it",new SpringManagedTransactionFactory(),dataSource));
            configuration.setMapUnderscoreToCamelCase(true);
            String resource="mapper/file/FileObjectMapper.xml";
            try(InputStream input=Resources.getResourceAsStream(resource))
            {
                new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
            }
            SqlSessionFactory sessions=new SqlSessionFactoryBuilder().build(configuration);
            FileObjectMapper mapper=new SqlSessionTemplate(sessions).getMapper(FileObjectMapper.class);
            return new Harness(adminUrl,user,password,schema,dataSource,mapper);
        }

        FileObjectMapper mapper(Invocation invocation)
        {
            return (FileObjectMapper)Proxy.newProxyInstance(FileObjectMapper.class.getClassLoader(),
                new Class<?>[]{FileObjectMapper.class},(proxy,method,args)->{
                    CheckedCall proceed=()->{
                        try{return method.invoke(delegate,args);}
                        catch(InvocationTargetException wrapped){throw wrapped.getCause();}
                    };
                    return invocation.invoke(method,proceed);
                });
        }

        FileObjectService service(FileObjectMapper mapper)
        {
            FileObjectRepository repository=new MyBatisFileObjectRepository(mapper);
            FileBusinessAccessChecker checker=new FileBusinessAccessChecker()
            {
                @Override public boolean supports(String businessType){return true;}
                @Override public boolean canRead(String businessType,Long businessId,Long userId,Long deptId)
                {return OWNER.userId().equals(userId)&&OWNER.deptId().equals(deptId);}
            };
            return new FileObjectService(repository,storage,
                new FileAccessPolicy(repository,List.of(checker)),cleanup,new FileContentPolicy());
        }

        <T> T tx(Work<T> work)
        {
            return transaction.execute(status->work.run());
        }

        void fixture(int relationCount) throws Exception
        {
            try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement())
            {
                statement.executeUpdate("""
                    insert into file_object(file_object_id,logical_name,current_version_no,next_version_no,status,created_by)
                    values(1,'proof',2,3,'ACTIVE',7)
                    """);
                statement.executeUpdate("""
                    insert into file_object_version(
                      file_version_id,file_object_id,version_no,storage_provider,object_key,original_file_name,
                      content_type,size_bytes,sha256,change_description,created_by)
                    values
                      (21,1,1,'LOCAL','objects/first','first.pdf','application/pdf',3,
                       repeat('a',64),'first',7),
                      (22,1,2,'LOCAL','objects/second','second.pdf','application/pdf',3,
                       repeat('b',64),'second',7)
                    """);
                statement.executeUpdate("""
                    insert into file_business_relation(
                      relation_id,file_object_id,business_type,business_id,material_type,visibility,
                      scope_dept_id,scope_user_id,created_by,created_dept_id,active)
                    values(11,1,'LEAD',9001,'CONTACT_PROOF','BUSINESS',0,0,7,3,1)
                    """);
                if(relationCount==2)
                    statement.executeUpdate("""
                        insert into file_business_relation(
                          relation_id,file_object_id,business_type,business_id,material_type,visibility,
                          scope_dept_id,scope_user_id,created_by,created_dept_id,active)
                        values(12,1,'CASE',9012,'CONTACT_PROOF','BUSINESS',0,0,7,3,1)
                        """);
            }
            storage.put("objects/first");
            storage.put("objects/second");
        }

        void assertNoBrokenObjectRelationState() throws Exception
        {
            assertEquals(0,count("""
                select count(*) from file_object o
                where o.status='ACTIVE' and not exists(
                  select 1 from file_business_relation r
                  where r.file_object_id=o.file_object_id and r.active=1)
                """));
            assertEquals(0,count("""
                select count(*) from file_business_relation r
                join file_object o on o.file_object_id=r.file_object_id
                where r.active=1 and o.status='DISABLED'
                """));
        }

        void assertOneCleanupSet(int versions,int retiredRelations) throws Exception
        {
            assertEquals(versions,count("select count(*) from file_storage_cleanup"));
            assertEquals(versions,count("select count(distinct target_key) from file_storage_cleanup"));
            assertEquals(versions,storage.totalDeletes());
            assertEquals(1,storage.deletes("objects/first"));
            assertEquals(1,storage.deletes("objects/second"));
            assertEquals(versions,cleanup.successes());
            assertEquals(retiredRelations,count(
                "select count(*) from file_relation_action where action_type like 'RETIRE_%'"));
        }

        int count(String sql) throws Exception
        {
            return Integer.parseInt(text(sql));
        }

        String text(String sql) throws Exception
        {
            try(Connection connection=dataSource.getConnection();
                Statement statement=connection.createStatement();
                ResultSet rows=statement.executeQuery(sql))
            {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }

        @Override public void close() throws Exception
        {
            try(Connection connection=DriverManager.getConnection(adminUrl,user,password);
                Statement statement=connection.createStatement())
            {
                statement.execute("drop database if exists "+schema);
            }
        }
    }

    private static final class RecordingStorage implements FileStoragePort
    {
        private final Map<String,AtomicInteger> deletes=new ConcurrentHashMap<>();
        void put(String key){deletes.put(key,new AtomicInteger());}
        int deletes(String key){return deletes.getOrDefault(key,new AtomicInteger()).get();}
        int totalDeletes(){return deletes.values().stream().mapToInt(AtomicInteger::get).sum();}
        @Override public void delete(String key){deletes.computeIfAbsent(key,ignored->new AtomicInteger()).incrementAndGet();}
        @Override public StagedObject stage(InputStream input,long size,String sha256){throw unsupported();}
        @Override public DetectedContentType inspect(StagedObject staged){throw unsupported();}
        @Override public StoredObject publish(StagedObject staged,String objectKey){throw unsupported();}
        @Override public void abort(StagedObject staged){throw unsupported();}
        @Override public InputStream read(String objectKey){throw unsupported();}
    }

    private static final class RecordingCleanup implements FileCleanupAuditPort
    {
        private final AtomicInteger successes=new AtomicInteger();
        int successes(){return successes.get();}
        @Override public Long beginCleanup(Long fileObjectId,String actionId,String targetType,String targetKey,
            FileActor actor){throw unsupported();}
        @Override public void recordCleanupSuccess(Long cleanupTaskId,String reason,FileActor actor)
        {successes.incrementAndGet();}
        @Override public void recordCleanupFailure(Long cleanupTaskId,String code,String message,FileActor actor)
        {throw new AssertionError("cleanup unexpectedly failed: "+code);}
    }

    private static UnsupportedOperationException unsupported()
    {return new UnsupportedOperationException("not used by lock-order test");}

    private static void createTables(DataSource dataSource) throws Exception
    {
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement())
        {
            for(String ddl:List.of(
                """
                create table file_object(
                  file_object_id bigint primary key,logical_name varchar(255) not null,
                  current_version_no int not null,next_version_no int not null,status varchar(16) not null,
                  created_by bigint not null,create_time datetime not null default current_timestamp,
                  update_time datetime not null default current_timestamp,version int not null default 0
                ) engine=innodb
                """,
                """
                create table file_object_version(
                  file_version_id bigint primary key,file_object_id bigint not null,version_no int not null,
                  storage_provider varchar(32) not null,object_key varchar(300) not null,
                  original_file_name varchar(255) not null,content_type varchar(160) not null,
                  size_bytes bigint not null,sha256 char(64) not null,change_description varchar(500) not null,
                  created_by bigint not null,create_time datetime not null default current_timestamp,
                  unique key uk_file_object_version(file_object_id,version_no),
                  unique key uk_file_storage_object(storage_provider,object_key)
                ) engine=innodb
                """,
                """
                create table file_business_relation(
                  relation_id bigint not null auto_increment primary key,file_object_id bigint not null,
                  business_type varchar(64) not null,business_id bigint not null,material_type varchar(96) not null,
                  visibility varchar(16) not null,scope_dept_id bigint not null,scope_user_id bigint not null,
                  created_by bigint not null,created_dept_id bigint null,active tinyint not null,
                  active_scope tinyint generated always as(case when active=1 then 1 else null end) stored,
                  create_time datetime not null default current_timestamp,revoke_time datetime null,
                  unique key uk_file_active_scope(
                    file_object_id,business_type,business_id,material_type,visibility,
                    scope_dept_id,scope_user_id,active_scope)
                ) engine=innodb
                """,
                """
                create table file_relation_action(
                  actor_id bigint not null,action_id varchar(128) not null,action_type varchar(16) not null,
                  relation_id bigint not null,request_fingerprint char(64) not null,created_at datetime not null,
                  primary key(actor_id,action_id)
                ) engine=innodb
                """,
                """
                create table file_lifecycle_audit(
                  lifecycle_audit_id bigint not null auto_increment primary key,file_object_id bigint not null,
                  file_version_id bigint null,relation_id bigint null,action_id varchar(128) null,
                  event_type varchar(32) not null,details varchar(1000) null,actor_id bigint not null,
                  actor_dept_id bigint null,occurred_at datetime not null,
                  unique key uk_file_lifecycle_action_event(
                    actor_id,action_id,file_object_id,relation_id,event_type)
                ) engine=innodb
                """,
                """
                create table file_storage_cleanup(
                  cleanup_task_id bigint not null auto_increment primary key,file_object_id bigint not null,
                  action_id varchar(128) null,target_type varchar(16) not null,target_key varchar(300) not null,
                  status varchar(16) not null,retry_count int not null,last_error_code varchar(120) null,
                  last_error_message varchar(500) null,next_retry_at datetime not null,actor_id bigint not null,
                  actor_dept_id bigint null,completed_at datetime null,create_time datetime not null,
                  update_time datetime not null,
                  unique key uk_file_cleanup_action_target(
                    actor_id,action_id,file_object_id,target_type,target_key)
                ) engine=innodb
                """))
                statement.execute(ddl);
        }
    }

    private static String required(String name)
    {
        String value=System.getenv(name);
        if(value==null||value.isBlank())throw new IllegalStateException(name+" is required");
        return value;
    }

    private static String withSchema(String url,String schema)
    {
        int query=url.indexOf('?');
        String prefix=query<0?url:url.substring(0,query);
        String suffix=query<0?"":url.substring(query);
        int slash=prefix.lastIndexOf('/');
        if(slash<"jdbc:mysql://".length())throw new IllegalArgumentException("MySQL URL must include a database");
        return prefix.substring(0,slash+1)+schema+suffix;
    }
}
