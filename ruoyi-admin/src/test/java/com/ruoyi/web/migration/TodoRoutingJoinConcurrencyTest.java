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

class TodoRoutingJoinConcurrencyTest
{
    @Test void transactionStartedBeforeFirstArrivalUsesCurrentLockedReadAfterWaiting() throws Exception
    {
        String url=MigrationTestDatabase.migrate();String user=MigrationTestDatabase.user(),password=MigrationTestDatabase.password();
        long root=9_700_001L;
        try(Connection setup=DriverManager.getConnection(url,user,password);Statement statement=setup.createStatement())
        {
            statement.executeUpdate("delete from todo_route_token where root_todo_id="+root);
            statement.executeUpdate("delete from todo_route_join where root_todo_id="+root);
            statement.executeUpdate("insert into todo_route_join(root_todo_id,node_key,occurrence,join_mode,required_branches,status) values("+root+",'join',0,'ALL','[\"a\",\"b\"]','WAITING')");
        }
        try(Connection branchA=DriverManager.getConnection(url,user,password);Connection branchB=DriverManager.getConnection(url,user,password))
        {
            branchA.setAutoCommit(false);branchB.setAutoCommit(false);
            lockJoin(branchA,root);
            insertArrival(branchA,root,"a");
            CountDownLatch attemptingLock=new CountDownLatch(1);
            ExecutorService executor=Executors.newSingleThreadExecutor();
            Future<Integer> advanced=executor.submit(()->{
                attemptingLock.countDown();
                lockJoin(branchB,root);
                insertArrival(branchB,root,"b");
                int arrivals=countArrivalsForUpdate(branchB,root);
                int updated;
                try(PreparedStatement update=branchB.prepareStatement("update todo_route_join set status='ADVANCED' where root_todo_id=? and node_key='join' and occurrence=0 and status='WAITING'"))
                {update.setLong(1,root);updated=arrivals==2?update.executeUpdate():0;}
                branchB.commit();return updated;
            });
            assertTrue(attemptingLock.await(5,TimeUnit.SECONDS));
            Thread.sleep(100);
            branchA.commit();
            assertEquals(1,advanced.get(10,TimeUnit.SECONDS));
            executor.shutdownNow();
        }
        try(Connection verify=DriverManager.getConnection(url,user,password);PreparedStatement select=verify.prepareStatement("select status from todo_route_join where root_todo_id=? and node_key='join' and occurrence=0"))
        {select.setLong(1,root);try(ResultSet rows=select.executeQuery()){assertTrue(rows.next());assertEquals("ADVANCED",rows.getString(1));}}
    }

    private void lockJoin(Connection connection,long root)throws Exception
    {try(PreparedStatement select=connection.prepareStatement("select * from todo_route_join where root_todo_id=? and node_key='join' and occurrence=0 for update")){select.setLong(1,root);try(ResultSet rows=select.executeQuery()){assertTrue(rows.next());}}}
    private void insertArrival(Connection connection,long root,String branch)throws Exception
    {try(PreparedStatement insert=connection.prepareStatement("insert into todo_route_token(root_todo_id,node_key,branch_key,occurrence,definition_hash,route_token,status) values(?,'join',?,0,repeat('a',64),'{}','ARRIVED')")){insert.setLong(1,root);insert.setString(2,branch);assertEquals(1,insert.executeUpdate());}}
    private int countArrivalsForUpdate(Connection connection,long root)throws Exception
    {try(PreparedStatement select=connection.prepareStatement("select branch_key from todo_route_token where root_todo_id=? and node_key='join' and occurrence=0 and status='ARRIVED' for update")){select.setLong(1,root);int count=0;try(ResultSet rows=select.executeQuery()){while(rows.next())count++;}return count;}}
}
