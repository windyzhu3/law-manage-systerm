package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class TodoDefinitionLedgerConcurrencyTest
{
    @Test void concurrentIdenticalRollbackClaimReplaysTheCommittedDraft() throws Exception
    {
        String url=MigrationTestDatabase.migrate(),user=MigrationTestDatabase.user(),password=MigrationTestDatabase.password();
        String action="rollback-concurrent-"+System.nanoTime(),fingerprint="a".repeat(64);long resultVersion=9_800_001L;
        try(Connection setup=DriverManager.getConnection(url,user,password);PreparedStatement cleanup=setup.prepareStatement("delete from todo_definition_action where action_id=?"))
        {cleanup.setString(1,action);cleanup.executeUpdate();}
        try(Connection first=DriverManager.getConnection(url,user,password);Connection second=DriverManager.getConnection(url,user,password))
        {
            first.setAutoCommit(false);second.setAutoCommit(false);assertEquals(1,insertClaim(first,action,fingerprint));lock(first,action);
            CountDownLatch inserting=new CountDownLatch(1);ExecutorService executor=Executors.newSingleThreadExecutor();
            Future<Long> replay=executor.submit(()->{inserting.countDown();assertEquals(0,insertClaim(second,action,fingerprint));long id=lock(second,action);second.commit();return id;});
            assertTrue(inserting.await(5,TimeUnit.SECONDS));Thread.sleep(100);
            try(PreparedStatement complete=first.prepareStatement("update todo_definition_action set action_status='APPLIED',entity_id=? where action_id=? and request_fingerprint=? and action_status='CLAIMED'"))
            {complete.setLong(1,resultVersion);complete.setString(2,action);complete.setString(3,fingerprint);assertEquals(1,complete.executeUpdate());}
            first.commit();assertEquals(resultVersion,replay.get(10,TimeUnit.SECONDS));executor.shutdownNow();
        }
    }

    @Test void differentRollbackActionsRacingTheSameTargetVersionHitTheUniqueVersionFence() throws Exception
    {
        String url=MigrationTestDatabase.migrate(),user=MigrationTestDatabase.user(),password=MigrationTestDatabase.password();
        String suffix=String.valueOf(System.nanoTime()),templateCode="ROLLBACK_RACE_"+suffix;
        String firstAction="rollback-race-a-"+suffix,secondAction="rollback-race-b-"+suffix;long templateId;
        try(Connection setup=DriverManager.getConnection(url,user,password))
        {
            try(PreparedStatement insert=setup.prepareStatement("insert into todo_template(template_code,template_name,business_type,status) values(?,?,'TEST','0')",Statement.RETURN_GENERATED_KEYS))
            {insert.setString(1,templateCode);insert.setString(2,templateCode);assertEquals(1,insert.executeUpdate());try(ResultSet keys=insert.getGeneratedKeys()){assertTrue(keys.next());templateId=keys.getLong(1);}}
            insertRollbackAction(setup,firstAction);insertRollbackAction(setup,secondAction);
        }
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try(Connection first=DriverManager.getConnection(url,user,password);Connection second=DriverManager.getConnection(url,user,password))
        {
            first.setAutoCommit(false);second.setAutoCommit(false);assertEquals(1,insertDraft(first,templateId,77));
            CountDownLatch inserting=new CountDownLatch(1);
            Future<Integer> collision=executor.submit(()->{inserting.countDown();try{insertDraft(second,templateId,77);second.commit();return 0;}catch(SQLException expected){second.rollback();return expected.getErrorCode();}});
            assertTrue(inserting.await(5,TimeUnit.SECONDS));Thread.sleep(100);first.commit();
            assertEquals(1062,collision.get(10,TimeUnit.SECONDS));
        }
        finally
        {
            executor.shutdownNow();
            try(Connection cleanup=DriverManager.getConnection(url,user,password);PreparedStatement actions=cleanup.prepareStatement("delete from todo_definition_action where action_id in (?,?)");PreparedStatement versions=cleanup.prepareStatement("delete from todo_template_version where template_id=?");PreparedStatement template=cleanup.prepareStatement("delete from todo_template where template_id=?"))
            {actions.setString(1,firstAction);actions.setString(2,secondAction);actions.executeUpdate();versions.setLong(1,templateId);versions.executeUpdate();template.setLong(1,templateId);template.executeUpdate();}
        }
    }

    @Test void preflightSnapshotCannotOverwriteAConcurrentlySavedDraft() throws Exception
    {
        String url=MigrationTestDatabase.migrate(),user=MigrationTestDatabase.user(),password=MigrationTestDatabase.password();
        String suffix=String.valueOf(System.nanoTime()),templateCode="PREFLIGHT_GUARD_"+suffix;long templateId=0,versionId=0;
        String oldDefinition="{\"revision\":1}",newDefinition="{\"revision\":2}",snapshotDefinition="{\"revision\":1,\"snapshotted\":true}";
        String owner="{\"type\":\"USER\"}",dod="{\"fields\":[]}",sla="{\"minutes\":30}",next="{\"type\":\"END\"}",ui="{\"fields\":[]}";
        try(Connection setup=DriverManager.getConnection(url,user,password))
        {
            try(PreparedStatement insert=setup.prepareStatement("insert into todo_template(template_code,template_name,business_type,status) values(?,?,'TEST','0')",Statement.RETURN_GENERATED_KEYS))
            {insert.setString(1,templateCode);insert.setString(2,templateCode);assertEquals(1,insert.executeUpdate());try(ResultSet keys=insert.getGeneratedKeys()){assertTrue(keys.next());templateId=keys.getLong(1);}}
            try(PreparedStatement insert=setup.prepareStatement("insert into todo_template_version(template_id,version_no,status,definition_schema_version,definition_json,owner_rule_json,dod_rule_json,sla_rule_json,next_rule_json,ui_schema_json) values(?,1,'DRAFT',1,cast(? as json),cast(? as json),cast(? as json),cast(? as json),cast(? as json),cast(? as json))",Statement.RETURN_GENERATED_KEYS))
            {insert.setLong(1,templateId);insert.setString(2,oldDefinition);insert.setString(3,owner);insert.setString(4,dod);insert.setString(5,sla);insert.setString(6,next);insert.setString(7,ui);assertEquals(1,insert.executeUpdate());try(ResultSet keys=insert.getGeneratedKeys()){assertTrue(keys.next());versionId=keys.getLong(1);}}
        }
        try(Connection preflight=DriverManager.getConnection(url,user,password);Connection save=DriverManager.getConnection(url,user,password))
        {
            preflight.setAutoCommit(false);
            try(PreparedStatement read=preflight.prepareStatement("select json_extract(definition_json,'$.revision') from todo_template_version where version_id=?"))
            {read.setLong(1,versionId);try(ResultSet rows=read.executeQuery()){assertTrue(rows.next());assertEquals(1,rows.getInt(1));}}
            try(PreparedStatement update=save.prepareStatement("update todo_template_version set definition_json=cast(? as json) where version_id=?"))
            {update.setString(1,newDefinition);update.setLong(2,versionId);assertEquals(1,update.executeUpdate());}
            try(PreparedStatement snapshot=preflight.prepareStatement("update todo_template_version set definition_json=cast(? as json) where version_id=? and status in ('DRAFT','BLOCKED') and definition_json <=> cast(? as json) and owner_rule_json <=> cast(? as json) and dod_rule_json <=> cast(? as json) and sla_rule_json <=> cast(? as json) and next_rule_json <=> cast(? as json) and ui_schema_json <=> cast(? as json)"))
            {snapshot.setString(1,snapshotDefinition);snapshot.setLong(2,versionId);snapshot.setString(3,oldDefinition);snapshot.setString(4,owner);snapshot.setString(5,dod);snapshot.setString(6,sla);snapshot.setString(7,next);snapshot.setString(8,ui);assertEquals(0,snapshot.executeUpdate());}
            preflight.rollback();
            try(PreparedStatement verify=save.prepareStatement("select json_extract(definition_json,'$.revision') from todo_template_version where version_id=?"))
            {verify.setLong(1,versionId);try(ResultSet rows=verify.executeQuery()){assertTrue(rows.next());assertEquals(2,rows.getInt(1));}}
        }
        finally
        {
            try(Connection cleanup=DriverManager.getConnection(url,user,password);PreparedStatement versions=cleanup.prepareStatement("delete from todo_template_version where template_id=?");PreparedStatement template=cleanup.prepareStatement("delete from todo_template where template_id=?"))
            {versions.setLong(1,templateId);versions.executeUpdate();template.setLong(1,templateId);template.executeUpdate();}
        }
    }

    private void insertRollbackAction(Connection c,String action)throws Exception
    {try(PreparedStatement insert=c.prepareStatement("insert into todo_definition_action(action_id,action_type,action_status,request_fingerprint,entity_type,source_entity_id,operator_id,operator_name,payload_json) values(?,'ROLLBACK_DRAFT','CLAIMED',?,'VERSION',9,7,'alice','{}')")){insert.setString(1,action);insert.setString(2,"b".repeat(64));assertEquals(1,insert.executeUpdate());}}
    private int insertDraft(Connection c,long templateId,int versionNo)throws Exception
    {try(PreparedStatement insert=c.prepareStatement("insert into todo_template_version(template_id,version_no,status,definition_schema_version) values(?,?,'DRAFT',1)")){insert.setLong(1,templateId);insert.setInt(2,versionNo);return insert.executeUpdate();}}

    private int insertClaim(Connection c,String action,String fingerprint)throws Exception
    {try(PreparedStatement insert=c.prepareStatement("insert ignore into todo_definition_action(action_id,action_type,action_status,request_fingerprint,entity_type,source_entity_id,operator_id,operator_name,payload_json) values(?,'ROLLBACK_DRAFT','CLAIMED',?,'VERSION',9,7,'alice','{}')")){insert.setString(1,action);insert.setString(2,fingerprint);return insert.executeUpdate();}}
    private long lock(Connection c,String action)throws Exception
    {try(PreparedStatement select=c.prepareStatement("select entity_id from todo_definition_action where action_id=? for update")){select.setString(1,action);try(ResultSet rows=select.executeQuery()){assertTrue(rows.next());return rows.getLong(1);}}}
}
