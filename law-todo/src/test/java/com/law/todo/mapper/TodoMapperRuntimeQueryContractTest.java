package com.law.todo.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TodoMapperRuntimeQueryContractTest
{
    @Test
    void runtimeLeadIngressLookupRequiresTheGovernedTd001EntrySlot() throws IOException
    {
        try(InputStream resource=getClass().getResourceAsStream("/mapper/todo/TodoMapper.xml"))
        {
            assertThat(resource).isNotNull();
            String mapper=new String(resource.readAllBytes(),StandardCharsets.UTF_8);
            String runtimeQuery=mapper.substring(mapper.indexOf("id=\"selectTriggerRules\""),
                    mapper.indexOf("</select>",mapper.indexOf("id=\"selectTriggerRules\"")));
            assertThat(runtimeQuery).contains("r.entry_slot_code='LEAD_FIRST_CONTACT_ENTRY'",
                    "t.template_code='TD-001'");
        }
    }
}
