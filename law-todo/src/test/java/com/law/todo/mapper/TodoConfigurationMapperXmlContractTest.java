package com.law.todo.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class TodoConfigurationMapperXmlContractTest
{
    @Test void mapperDefinesRuleAndProjectionStatements() throws Exception
    {
        String xml=resource("mapper/todo/TodoConfigurationMapper.xml");

        for (String id : List.of("selectSlaRules","selectSlaRule","insertSlaRule","updateSlaRuleConditionally",
                "selectDodRules","selectDodRule","insertDodRule","updateDodRuleConditionally",
                "replaceDraftRuleRefs","selectTemplateConfiguration","selectReleaseRecords",
                "insertSimulationRecord"))
            assertTrue(xml.contains("id=\""+id+"\""),id);
    }

    private String resource(String path) throws Exception
    {
        try (InputStream input=getClass().getClassLoader().getResourceAsStream(path))
        {
            assertTrue(input!=null,"missing resource: "+path);
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
}
