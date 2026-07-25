package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import com.ruoyi.system.domain.BizLeadCallRecord;
import com.ruoyi.system.domain.BizLeadFollowup;
import com.ruoyi.system.domain.BusinessEventRecord;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.BusinessEventMapper;
import com.ruoyi.system.mapper.LeadFlowMapper;

class LeadFlowMapperExternalMysqlIT
{
    @Test
    void factLeadTransitionAndOutboxShareOneRealMysqlTransaction() throws Exception
    {
        String url=requiredEnvironment("TODO_MIGRATION_DB_URL");
        String user=requiredEnvironment("TODO_MIGRATION_DB_USER");
        String password=requiredEnvironment("TODO_MIGRATION_DB_PASSWORD");
        long leadId=900_000_000L+System.nanoTime()%10_000_000L;
        String leadNo="TASK6-"+leadId;
        DataSource dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,user,password);
        insertLead(dataSource,leadId,leadNo);
        SqlSessionFactory sessions=new SqlSessionFactoryBuilder().build(myBatis(dataSource));
        String eventKey="LEAD_FIRST_CONTACT_VALID:"+leadId+":TX";
        String callKey="LEAD_CALL:MANUAL:TX-"+leadId;
        try
        {
            try(SqlSession session=sessions.openSession(false))
            {
                writeFlow(session,leadId,leadNo,callKey,eventKey);
                session.rollback();
            }
            assertState(dataSource,leadId,eventKey,0,0,0,0,null);

            try(SqlSession session=sessions.openSession(false))
            {
                writeFlow(session,leadId,leadNo,callKey,eventKey);
                session.commit();
            }
            assertState(dataSource,leadId,eventKey,1,1,1,1,"VALID");
        }
        finally
        {
            cleanup(dataSource,leadId,eventKey);
        }
    }

    private static void writeFlow(SqlSession session,long leadId,String leadNo,String callKey,String eventKey)
    {
        LeadFlowMapper facts=session.getMapper(LeadFlowMapper.class);
        BizLeadMapper leads=session.getMapper(BizLeadMapper.class);
        BusinessEventMapper events=session.getMapper(BusinessEventMapper.class);
        BizLeadCallRecord call=new BizLeadCallRecord();
        call.setLeadId(leadId);call.setTodoId(1L);call.setCallChannel("MANUAL");
        call.setExternalCallId("TX-"+leadId);call.setStartedAt(LocalDateTime.of(2026,7,25,9,0));
        call.setCallResult("CONNECTED");call.setIdempotencyKey(callKey);call.setCreateBy("task6");
        assertEquals(1,facts.insertCallRecordIfAbsent(call));

        BizLeadFollowup followup=new BizLeadFollowup();
        followup.setLeadId(leadId);followup.setFollowType("phone");followup.setFollowResult("VALID");
        followup.setContent("transaction");followup.setFollowUserId(1L);followup.setCreateBy("task6");
        assertEquals(1,leads.insertFollowup(followup));
        assertEquals(1,leads.completeFirstContact(leadId,"1","VALID","Client","Shanghai",
                "Demand","1",null,null,0,"task6"));

        BusinessEventRecord event=new BusinessEventRecord();
        event.setEventType("LEAD_FIRST_CONTACT_VALID");event.setPayloadVersion(1);
        event.setAggregateType("LEAD");event.setAggregateId(leadId);event.setAggregateNo(leadNo);
        event.setIdempotencyKey(eventKey);event.setPayload("{\"schemaVersion\":1}");
        event.setEventStatus("PENDING");event.setRetryCount(0);event.setCreateBy("task6");
        assertEquals(1,events.insertBusinessEvent(event));
    }

    private static Configuration myBatis(DataSource dataSource) throws Exception
    {
        Configuration configuration=new Configuration(new Environment("lead-flow-it",
                new JdbcTransactionFactory(),dataSource));
        configuration.getTypeAliasRegistry().registerAliases("com.ruoyi.system.domain");
        for(String resource:new String[]{"mapper/system/BizLeadMapper.xml",
                "mapper/system/LeadFlowMapper.xml","mapper/system/BusinessEventMapper.xml"})
        {
            try(InputStream input=Resources.getResourceAsStream(resource))
            {
                new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }

    private static void insertLead(DataSource dataSource,long leadId,String leadNo) throws Exception
    {
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement())
        {
            statement.executeUpdate("insert into biz_lead(lead_id,lead_no,lead_name,status,pool_status,"
                    +"disposition,del_flag,owner_id,row_version) values("+leadId+",'"+leadNo
                    +"','Task 6','1','0','ACTIVE','0',1,0)");
        }
    }

    private static void assertState(DataSource dataSource,long leadId,String eventKey,int calls,int followups,
            int events,int version,String result) throws Exception
    {
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement())
        {
            assertEquals(calls,count(statement,"select count(*) from biz_lead_call_record where lead_id="+leadId));
            assertEquals(followups,count(statement,"select count(*) from biz_lead_followup where lead_id="+leadId));
            assertEquals(events,count(statement,"select count(*) from business_event where idempotency_key='"+eventKey+"'"));
            try(ResultSet row=statement.executeQuery("select row_version,first_contact_result from biz_lead where lead_id="+leadId))
            {
                row.next();assertEquals(version,row.getInt(1));assertEquals(result,row.getString(2));
            }
        }
    }

    private static int count(Statement statement,String sql) throws Exception
    {
        try(ResultSet rows=statement.executeQuery(sql)){rows.next();return rows.getInt(1);}
    }

    private static void cleanup(DataSource dataSource,long leadId,String eventKey) throws Exception
    {
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement())
        {
            statement.executeUpdate("delete from business_event where idempotency_key='"+eventKey+"'");
            statement.executeUpdate("delete from biz_lead_call_record where lead_id="+leadId);
            statement.executeUpdate("delete from biz_lead_followup where lead_id="+leadId);
            statement.executeUpdate("delete from biz_lead where lead_id="+leadId);
        }
    }

    private static String requiredEnvironment(String name)
    {
        String value=System.getenv(name);
        if(value==null||value.isBlank())throw new IllegalStateException(name+" is required");
        return value;
    }
}
