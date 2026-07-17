package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class TodoAutoActionFencingConcurrencyTest
{
    @Test void executionFenceSerializesCommandCommitAndStaleDeadFinalizer() throws Exception
    {
        String url=MigrationTestDatabase.migrate(),user=MigrationTestDatabase.user(),password=MigrationTestDatabase.password();
        String commandFirst="AUTO:9700001:fence",deadFirst="AUTO:9700002:fence";
        try(Connection setup=DriverManager.getConnection(url,user,password);Statement sql=setup.createStatement())
        {
            sql.executeUpdate("delete from todo_action_log where action_id in ('"+commandFirst+"','"+deadFirst+"')");
            sql.executeUpdate("delete from todo_auto_action_audit where execution_key in ('"+commandFirst+"','"+deadFirst+"')");
            sql.executeUpdate("delete from todo_auto_action_execution where execution_key in ('"+commandFirst+"','"+deadFirst+"')");
            insertExecution(setup,commandFirst,9_700_001L);insertExecution(setup,deadFirst,9_700_002L);
        }

        try(Connection command=DriverManager.getConnection(url,user,password);Connection finalizer=DriverManager.getConnection(url,user,password))
        {
            command.setAutoCommit(false);finalizer.setAutoCommit(false);assertEquals("CLAIMED",lockStatus(command,commandFirst));insertAction(command,commandFirst,9_700_001L);
            CountDownLatch attempted=new CountDownLatch(1);ExecutorService executor=Executors.newSingleThreadExecutor();Future<Integer> dead=executor.submit(()->{attempted.countDown();int changed=finalizeDead(finalizer,commandFirst);finalizer.commit();return changed;});
            assertTrue(attempted.await(5,TimeUnit.SECONDS));Thread.sleep(100);assertFalse(dead.isDone());command.commit();assertEquals(0,dead.get(10,TimeUnit.SECONDS));executor.shutdownNow();
        }
        try(Connection verify=DriverManager.getConnection(url,user,password))
        {assertEquals("CLAIMED",status(verify,commandFirst));assertEquals(1,count(verify,"select count(*) from todo_action_log where action_id='"+commandFirst+"'"));}

        try(Connection finalizer=DriverManager.getConnection(url,user,password);Connection command=DriverManager.getConnection(url,user,password))
        {
            finalizer.setAutoCommit(false);assertEquals(1,finalizeDead(finalizer,deadFirst));finalizer.commit();command.setAutoCommit(false);assertEquals("DEAD",lockStatus(command,deadFirst));command.rollback();
        }
        try(Connection verify=DriverManager.getConnection(url,user,password))
        {assertEquals("DEAD",status(verify,deadFirst));assertEquals(0,count(verify,"select count(*) from todo_action_log where action_id='"+deadFirst+"'"));}
    }

    private void insertExecution(Connection connection,String key,long todoId)throws Exception
    {try(PreparedStatement insert=connection.prepareStatement("insert into todo_auto_action_execution(execution_key,todo_id,rule_key,action_type,status,attempt_count,claimed_at) values(?,?,'fence','ESCALATE','CLAIMED',2,date_sub(sysdate(),interval 1 hour))")){insert.setString(1,key);insert.setLong(2,todoId);assertEquals(1,insert.executeUpdate());}}
    private void insertAction(Connection connection,String key,long todoId)throws Exception
    {try(PreparedStatement insert=connection.prepareStatement("insert into todo_action_log(todo_id,action_id,action_type,action_source,from_status,to_status,operator_id,operator_name) values(?,?,'ESCALATE','SYSTEM','SUBMITTED','SUBMITTED',-1,'TODO_AUTO_ACTION')")){insert.setLong(1,todoId);insert.setString(2,key);assertEquals(1,insert.executeUpdate());}}
    private int finalizeDead(Connection connection,String key)throws Exception
    {try(PreparedStatement update=connection.prepareStatement("update todo_auto_action_execution set status='DEAD',attempt_count=3,completed_at=sysdate(),last_error_code='STALE' where execution_key=? and status='CLAIMED' and attempt_count=2 and claimed_at<=sysdate() and not exists(select 1 from todo_action_log committed where committed.action_id=?)")){update.setString(1,key);update.setString(2,key);return update.executeUpdate();}}
    private String lockStatus(Connection connection,String key)throws Exception
    {try(PreparedStatement select=connection.prepareStatement("select status from todo_auto_action_execution where execution_key=? for update")){select.setString(1,key);try(ResultSet row=select.executeQuery()){assertTrue(row.next());return row.getString(1);}}}
    private String status(Connection connection,String key)throws Exception
    {try(PreparedStatement select=connection.prepareStatement("select status from todo_auto_action_execution where execution_key=?")){select.setString(1,key);try(ResultSet row=select.executeQuery()){assertTrue(row.next());return row.getString(1);}}}
    private long count(Connection connection,String query)throws Exception
    {try(Statement sql=connection.createStatement();ResultSet row=sql.executeQuery(query)){assertTrue(row.next());return row.getLong(1);}}
}
