package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.law.todo.domain.TodoException;

class TodoPayloadSchemaDescriptorTest
{
    private final TodoPayloadSchemaDescriptor descriptor=new TodoPayloadSchemaDescriptor();

    @Test void flattensTwoLevelsAndPreservesLabelsExamplesSensitivityAndOptions()
    {
        List<TodoPayloadSchemaDescriptor.PayloadFieldDescriptor> fields=descriptor.describe("""
                {"type":"object","required":["ownerId"],"properties":{
                  "ownerId":{"type":"integer","title":"负责人","examples":[7]},
                  "customer":{"type":"object","properties":{
                    "mobile":{"type":"string","title":"手机号","examples":["13800000000"],"x-sensitive":true},
                    "level":{"type":"string","title":"客户等级","enum":["A","B"]}
                  }}
                }}""","LEAD");

        assertThat(fields).extracting(TodoPayloadSchemaDescriptor.PayloadFieldDescriptor::path)
                .containsExactly("ownerId","customer.mobile","customer.level");
        assertThat(fields.get(0).label()).isEqualTo("负责人");
        assertThat(fields.get(0).example()).isEqualTo(7);
        assertThat(fields.get(1).sensitive()).isTrue();
        assertThat(fields.get(1).example()).isNull();
        assertThat(fields.get(2).options()).containsExactly("A","B");
        assertThat(fields.get(0).operators()).contains("EQ","GT","LTE","IN");
    }

    @Test void rejectsInvalidNonblankSchemaAndAllowsBlankSchema()
    {
        assertThatThrownBy(()->descriptor.describe("{invalid","LEAD"))
                .isInstanceOf(TodoException.class)
                .extracting(error->((TodoException)error).getBusinessCode()).isEqualTo("TODO_EVENT_SCHEMA_INVALID");

        assertThat(descriptor.describe("  ","LEAD")).isEmpty();
    }
}
