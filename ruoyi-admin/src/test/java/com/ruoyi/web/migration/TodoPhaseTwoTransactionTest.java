package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import org.junit.jupiter.api.Test;

class TodoPhaseTwoTransactionTest
{
    @Test void todoAndOutboxFactsCommitOrRollbackTogether() throws Exception
    {
        String url=MigrationTestDatabase.migrate();
        try(Connection c=DriverManager.getConnection(url,MigrationTestDatabase.user(),MigrationTestDatabase.password()))
        {
            try(Statement cleanup=c.createStatement()){cleanup.executeUpdate("delete from business_event where idempotency_key in ('tx-rollback','tx-commit')");}
            long template=id(c,"select template_id from todo_template where template_code='CONTRACT_REVIEW'");
            long version=id(c,"select version_id from todo_template_version where template_id="+template+" and version_no=1");
            long todo=insertTodo(c,template,version);c.setAutoCommit(false);
            update(c,"update todo_instance set status='COMPLETED' where todo_id="+todo+" and status='SUBMITTED'");insertEvent(c,"tx-rollback");c.rollback();
            assertEquals("SUBMITTED",text(c,"select status from todo_instance where todo_id="+todo));assertEquals(0,id(c,"select count(*) from business_event where idempotency_key='tx-rollback'"));
            update(c,"update todo_instance set status='COMPLETED' where todo_id="+todo+" and status='SUBMITTED'");insertEvent(c,"tx-commit");c.commit();
            assertEquals("COMPLETED",text(c,"select status from todo_instance where todo_id="+todo));assertEquals(1,id(c,"select count(*) from business_event where idempotency_key='tx-commit'"));c.setAutoCommit(true);
        }
    }
    private void insertEvent(Connection c,String key)throws SQLException{update(c,"insert into business_event(event_type,aggregate_type,aggregate_id,idempotency_key,payload,event_status,retry_count,next_retry_time,create_time) values('CONTRACT_APPROVED','CONTRACT',998,'"+key+"','{}','PENDING',0,sysdate(),sysdate())");}
    private long insertTodo(Connection c,long template,long version)throws SQLException{try(PreparedStatement p=c.prepareStatement("insert into todo_instance(todo_no,template_id,template_version_id,template_code,title,business_type,business_id,status) values(?,?,?,?,?,?,?,'SUBMITTED')",Statement.RETURN_GENERATED_KEYS)){p.setString(1,"TX"+System.nanoTime());p.setLong(2,template);p.setLong(3,version);p.setString(4,"CONTRACT_REVIEW");p.setString(5,"transaction test");p.setString(6,"CONTRACT");p.setLong(7,998);p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){assertTrue(r.next());return r.getLong(1);}}}
    private void update(Connection c,String sql)throws SQLException{try(Statement s=c.createStatement()){assertEquals(1,s.executeUpdate(sql));}}
    private long id(Connection c,String sql)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){assertTrue(r.next());return r.getLong(1);}}
    private String text(Connection c,String sql)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){assertTrue(r.next());return r.getString(1);}}
}
