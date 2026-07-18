package com.ruoyi.system.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class BusinessEventMapperXmlContractTest
{
    @Test void persistsAndReadsProducerPayloadVersion() throws Exception
    {
        try(InputStream input=getClass().getResourceAsStream("/mapper/system/BusinessEventMapper.xml"))
        {
            String xml=new String(input.readAllBytes(),StandardCharsets.UTF_8).replaceAll("\\s+"," ");
            assertTrue(xml.contains("event_type, payload_version, aggregate_type"));
            assertTrue(xml.contains("#{eventType}, #{payloadVersion}, #{aggregateType}"));
            assertTrue(xml.contains("payload_version payloadVersion"));
        }
    }
}
