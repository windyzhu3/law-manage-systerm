package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.sql.*;
import org.junit.jupiter.api.Test;

class PhaseTwoDatabaseInvariantTest
{
    @Test void phaseTwoDefinitionsAndConstraintsArePresent() throws Exception
    {
        String url=System.getenv("TODO_MIGRATION_DB_URL");assumeTrue(url!=null&&!url.isBlank());try(Connection c=DriverManager.getConnection(url,System.getenv("TODO_MIGRATION_DB_USER"),System.getenv("TODO_MIGRATION_DB_PASSWORD"))){assertEquals(14,count(c,"select count(*) from todo_template where template_code in ('CONTRACT_REVIEW','CONTRACT_SIGN','PAYMENT_CONFIRM','INVOICE_HANDLE','CASE_CREATE_CHECK','CASE_ASSIGN','CASE_ACCEPT','CASE_REASSIGN','CASE_TRANSFER_REVIEW','MATTER_NODE_HANDLE','MATTER_EXPENSE_REVIEW','MATTER_DOCUMENT_SUPPLY','CASE_CLOSE_CONFIRM','CASE_ARCHIVE_CONFIRM')"));assertEquals(14,count(c,"select count(*) from todo_template t join todo_template_version v on v.template_id=t.template_id where t.template_code<>'LEAD_FIRST_CONTACT' and v.version_no=1 and v.status='PUBLISHED'"));assertEquals(15,count(c,"select count(*) from todo_trigger_rule r join todo_template t on t.template_id=r.template_id where t.template_code<>'LEAD_FIRST_CONTACT' and r.enabled='Y'"));assertEquals(0,count(c,"select count(*) from todo_template_version where status='PUBLISHED' and owner_rule_json is null"));assertEquals(10,count(c,"select count(distinct perms) from sys_menu where perms in ('todo:chain:query','todo:operations:list','todo:force:complete','todo:force:cancel','todo:batch:transfer','todo:sla:waive','todo:regenerate','todo:definition:list','todo:definition:edit','todo:definition:publish')"));
            try(Statement s=c.createStatement()){s.executeUpdate("delete from todo_exception_log where action_id='invariant-action'");s.executeUpdate("insert into todo_exception_log(todo_id,action_id,operation_type,operator_id,operator_name,reason) values(1,'invariant-action','FORCE_CANCEL',1,'admin','test')");assertThrows(SQLException.class,()->s.executeUpdate("insert into todo_exception_log(todo_id,action_id,operation_type,operator_id,operator_name,reason) values(1,'invariant-action','FORCE_CANCEL',1,'admin','test')"));}
            try(ResultSet r=c.getMetaData().getColumns(c.getCatalog(),null,"todo_instance","template_code")){assertTrue(r.next());}
        }
    }
    private long count(Connection c,String sql)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){assertTrue(r.next());return r.getLong(1);}}
}
