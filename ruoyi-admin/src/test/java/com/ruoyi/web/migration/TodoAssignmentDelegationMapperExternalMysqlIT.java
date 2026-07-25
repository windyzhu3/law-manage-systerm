package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
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
import org.junit.jupiter.api.Test;

import com.law.todo.mapper.TodoMapper;

class TodoAssignmentDelegationMapperExternalMysqlIT
{
    private static final LocalDateTime EFFECTIVE_AT=LocalDateTime.of(2026,7,25,10,30);
    private static final String PRE_FIX_QUERY_PROPERTY="todo.assignment.it.preFixQuery";

    @Test
    void newestUnavailableDelegateFallsBackToOlderUsableDelegate() throws Exception
    {
        String adminUrl=requiredEnvironment("TODO_MIGRATION_DB_URL");
        String user=requiredEnvironment("TODO_MIGRATION_DB_USER");
        String password=requiredEnvironment("TODO_MIGRATION_DB_PASSWORD");
        String schema="todo_assignment_it_"+UUID.randomUUID().toString().replace("-","");
        createSchema(adminUrl,user,password,schema);
        try
        {
            String schemaUrl=withSchema(adminUrl,schema);
            DataSource dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",schemaUrl,user,password);
            createTablesAndFixtures(dataSource);
            Configuration configuration=myBatis(dataSource,Boolean.getBoolean(PRE_FIX_QUERY_PROPERTY));

            try(SqlSession session=new SqlSessionFactoryBuilder().build(configuration).openSession(true))
            {
                assertEquals(201L,session.getMapper(TodoMapper.class)
                        .selectActiveDelegate(100L,EFFECTIVE_AT));
            }
        }
        finally
        {
            dropSchema(adminUrl,user,password,schema);
        }
    }

    private static Configuration myBatis(DataSource dataSource,boolean preFixQuery) throws Exception
    {
        Configuration configuration=new Configuration(new Environment("todo-assignment-delegation-it",
                new JdbcTransactionFactory(),dataSource));
        String mapperResource="mapper/todo/TodoMapper.xml";
        byte[] mapperXml;
        try(InputStream input=Resources.getResourceAsStream(mapperResource))
        {
            mapperXml=input.readAllBytes();
        }
        if(preFixQuery)
        {
            String xml=new String(mapperXml,StandardCharsets.UTF_8);
            int query=xml.indexOf("<select id=\"selectActiveDelegate\"");
            int exclusion=xml.indexOf("      and not exists(",query);
            int ordering=xml.indexOf("    order by delegation.effective_from",exclusion);
            if(query<0||exclusion<0||ordering<0)
                throw new AssertionError("Pre-fix query fixture could not remove the target availability anti-join");
            mapperXml=(xml.substring(0,exclusion)+xml.substring(ordering)).getBytes(StandardCharsets.UTF_8);
        }
        try(InputStream input=new ByteArrayInputStream(mapperXml))
        {
            new XMLMapperBuilder(input,configuration,mapperResource,configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private static void createTablesAndFixtures(DataSource dataSource) throws Exception
    {
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement())
        {
            statement.execute("""
                    create table sys_user(
                      user_id bigint not null primary key,
                      status char(1) not null,
                      del_flag char(1) not null
                    ) engine=innodb
                    """);
            statement.execute("""
                    create table sys_user_delegation(
                      delegation_id bigint not null auto_increment primary key,
                      from_user_id bigint not null,
                      to_user_id bigint not null,
                      status varchar(16) not null,
                      effective_from datetime not null,
                      effective_to datetime null
                    ) engine=innodb
                    """);
            statement.execute("""
                    create table sys_user_availability(
                      availability_id bigint not null auto_increment primary key,
                      user_id bigint not null,
                      status varchar(16) not null,
                      effective_from datetime not null,
                      effective_to datetime null
                    ) engine=innodb
                    """);
            statement.executeUpdate("insert into sys_user(user_id,status,del_flag) values(201,'0','0'),(202,'0','0')");
        }
        try(Connection connection=dataSource.getConnection();
            PreparedStatement delegation=connection.prepareStatement("""
                    insert into sys_user_delegation(
                      from_user_id,to_user_id,status,effective_from,effective_to
                    ) values(?,?,'ACTIVE',?,?)
                    """);
            PreparedStatement unavailable=connection.prepareStatement("""
                    insert into sys_user_availability(
                      user_id,status,effective_from,effective_to
                    ) values(?,'UNAVAILABLE',?,?)
                    """))
        {
            delegation.setLong(1,100L);
            delegation.setLong(2,201L);
            delegation.setObject(3,EFFECTIVE_AT.minusDays(2));
            delegation.setObject(4,EFFECTIVE_AT.plusDays(2));
            delegation.executeUpdate();
            delegation.setLong(2,202L);
            delegation.setObject(3,EFFECTIVE_AT.minusDays(1));
            delegation.executeUpdate();

            unavailable.setLong(1,202L);
            unavailable.setObject(2,EFFECTIVE_AT.minusHours(1));
            unavailable.setObject(3,EFFECTIVE_AT.plusHours(1));
            unavailable.executeUpdate();
        }
    }

    private static void createSchema(String url,String user,String password,String schema) throws Exception
    {
        try(Connection connection=DriverManager.getConnection(url,user,password);
            Statement statement=connection.createStatement())
        {
            statement.execute("create database `"+schema+"` character set utf8mb4 collate utf8mb4_unicode_ci");
        }
    }

    private static void dropSchema(String url,String user,String password,String schema) throws Exception
    {
        try(Connection connection=DriverManager.getConnection(url,user,password);
            Statement statement=connection.createStatement())
        {
            statement.execute("drop database if exists `"+schema+"`");
        }
    }

    private static String withSchema(String url,String schema)
    {
        int query=url.indexOf('?');
        String base=query<0?url:url.substring(0,query);
        String parameters=query<0?"":url.substring(query);
        int slash=base.lastIndexOf('/');
        if(slash<"jdbc:mysql://".length())
            throw new IllegalArgumentException("TODO_MIGRATION_DB_URL must include a database name");
        return base.substring(0,slash+1)+schema+parameters;
    }

    private static String requiredEnvironment(String name)
    {
        String value=System.getenv(name);
        if(value==null||value.isBlank())
            throw new IllegalStateException(name+" is required for the external MySQL integration test");
        return value;
    }
}
