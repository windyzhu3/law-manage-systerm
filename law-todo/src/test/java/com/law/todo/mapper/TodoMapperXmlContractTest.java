package com.law.todo.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class TodoMapperXmlContractTest
{
    @Test
    void lifecycleUpdatePersistsMilestoneTimestamps() throws Exception
    {
        try (InputStream input = getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("claimed_at=case when #{toStatus}='CLAIMED'"));
            assertTrue(xml.contains("started_at=case when #{toStatus}='IN_PROGRESS'"));
            assertTrue(xml.contains("submitted_at=case when #{toStatus}='SUBMITTED'"));
            assertTrue(xml.contains("completed_at=case when #{toStatus}='COMPLETED'"));
            assertTrue(xml.contains("cancelled_at=case when #{toStatus}='CANCELLED'"));
            assertTrue(xml.contains("owner_dept_id=case when #{ownerId} is not null"));
        }
    }

    @Test
    void queueQueriesEnforceAllCandidateAndReadOnlyScopes() throws Exception
    {
        try (InputStream input = getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("c.candidate_type='ROLE'"));
            assertTrue(xml.contains("c.candidate_type='POST'"));
            assertTrue(xml.contains("from todo_cc cc"));
            assertTrue(xml.contains("led_dept.leader=viewer.user_name"));
            assertTrue(xml.contains("<include refid=\"visibleTodoPredicate\"/>"));
        }
    }
}
