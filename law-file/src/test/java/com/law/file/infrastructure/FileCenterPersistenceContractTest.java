package com.law.file.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FileCenterPersistenceContractTest
{
    @Test void migration_and_mapper_keep_security_and_lifecycle_invariants() throws Exception
    {
        String sql=Files.readString(Path.of("..","ruoyi-admin","src","main","resources","db","migration",
            "V0_20_6__file_center.sql")).toLowerCase().replaceAll("\\s+"," ");
        String mapper=Files.readString(Path.of("src","main","resources","mapper","file","FileObjectMapper.xml"))
            .toLowerCase().replaceAll("\\s+"," ");

        assertAll(
            ()->assertTrue(sql.contains("create table file_relation_action")),
            ()->assertTrue(sql.contains("request_fingerprint char(64)")),
            ()->assertTrue(sql.contains("scope_dept_id bigint not null")),
            ()->assertTrue(sql.contains("scope_user_id bigint not null")),
            ()->assertTrue(sql.contains("active_scope tinyint generated always")),
            ()->assertTrue(sql.contains("change_description varchar(500) not null")),
            ()->assertTrue(sql.contains("relation_id bigint not null, access_type")),
            ()->assertTrue(sql.contains("create table file_lifecycle_audit")),
            ()->assertTrue(sql.contains("create table file_storage_cleanup")),
            ()->assertTrue(sql.contains("status in ('pending','failed','completed')")),
            ()->assertTrue(sql.contains("idx_file_cleanup_retry (status,next_retry_at,cleanup_task_id)")),
            ()->assertTrue(sql.contains("last_error_code varchar(120)")),
            ()->assertTrue(sql.contains("access_session_id char(36) not null")),
            ()->assertTrue(sql.contains("outcome varchar(16) not null")),
            ()->assertFalse(sql.contains(" access_token varchar")),
            ()->assertTrue(mapper.contains("status='expired'")),
            ()->assertTrue(mapper.contains("relation_id relationid")),
            ()->assertTrue(mapper.contains("event_type eventtype")),
            ()->assertTrue(mapper.contains("status in ('pending','failed') and next_retry_at&lt;=#{readyat}")),
            ()->assertTrue(mapper.contains("for update skip locked"))
        );
    }
}
