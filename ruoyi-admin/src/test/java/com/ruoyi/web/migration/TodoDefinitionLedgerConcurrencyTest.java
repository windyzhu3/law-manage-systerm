package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
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

    private int insertClaim(Connection c,String action,String fingerprint)throws Exception
    {try(PreparedStatement insert=c.prepareStatement("insert ignore into todo_definition_action(action_id,action_type,action_status,request_fingerprint,entity_type,source_entity_id,operator_id,operator_name,payload_json) values(?,'ROLLBACK_DRAFT','CLAIMED',?,'VERSION',9,7,'alice','{}')")){insert.setString(1,action);insert.setString(2,fingerprint);return insert.executeUpdate();}}
    private long lock(Connection c,String action)throws Exception
    {try(PreparedStatement select=c.prepareStatement("select entity_id from todo_definition_action where action_id=? for update")){select.setString(1,action);try(ResultSet rows=select.executeQuery()){assertTrue(rows.next());return rows.getLong(1);}}}
}
