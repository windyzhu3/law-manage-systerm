package com.law.business.lead.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.law.business.lead.dto.LeadProgressCompleteCommand;
import com.law.business.lead.support.LeadProgressPayloadParser.PayloadValidationException;

class LeadProgressPayloadParserTest
{
    @Test
    void normalizesTheSameTypedCommandUsedBySimulationAndLiveCompletion()
    {
        LeadProgressCompleteCommand command=LeadProgressPayloadParser.parse(Map.of(
                "leadId"," 91 ","todoId",7001,
                "progressType"," PHONE ",
                "progressAt","2026-07-31T10:00:00.987654321",
                "remark"," completed contact "),91L,7001L);

        assertEquals(91L,command.getLeadId());
        assertEquals(7001L,command.getTodoId());
        assertEquals("PHONE",command.getProgressType());
        assertEquals(LocalDateTime.of(2026,7,31,10,0),command.getProgressAt());
        assertEquals("completed contact",command.getRemark());
    }

    @Test
    void acceptsAnAbsentOptionalRemarkAndSimulationTodoIdentity()
    {
        LeadProgressCompleteCommand command=LeadProgressPayloadParser.parse(Map.of(
                "progressType","WECHAT","progressAt",LocalDateTime.of(2026,7,31,11,0)),
                92L,null);

        assertEquals(92L,command.getLeadId());
        assertNull(command.getTodoId());
        assertNull(command.getRemark());
    }

    @Test
    void rejectsMalformedProgressDateWithTheStableHandlerError()
    {
        PayloadValidationException error=assertThrows(PayloadValidationException.class,
                ()->LeadProgressPayloadParser.parse(Map.of(
                        "progressType","PHONE","progressAt","invalid"),91L,7001L));

        assertEquals("TODO_HANDLER_PAYLOAD_INVALID",error.getBusinessCode());
        assertEquals("progressAt must be an ISO local date-time",error.getMessage());
    }

    @Test
    void rejectsNonStringProgressType()
    {
        assertInvalid("progressType must be a string",Map.of(
                "progressType",3,"progressAt","2026-07-31T10:00:00"),91L,7001L);
        assertInvalid("progressType must not be blank",Map.of(
                "progressType","   ","progressAt","2026-07-31T10:00:00"),91L,7001L);
    }

    @Test
    void rejectsInvalidOrMismatchedPayloadIdentities()
    {
        assertInvalid("leadId must be a positive integer",Map.of(
                "leadId",91.5,"progressType","PHONE",
                "progressAt","2026-07-31T10:00:00"),91L,7001L);
        assertInvalid("todoId must be a positive integer",Map.of(
                "todoId",0,"progressType","PHONE",
                "progressAt","2026-07-31T10:00:00"),91L,7001L);
        assertInvalid("leadId must match the source Todo",Map.of(
                "leadId",92,"progressType","PHONE",
                "progressAt","2026-07-31T10:00:00"),91L,7001L);
        assertInvalid("todoId must match the source Todo",Map.of(
                "todoId","7002","progressType","PHONE",
                "progressAt","2026-07-31T10:00:00"),91L,7001L);
    }

    @Test
    void rejectsNonStringOrOversizedRemark()
    {
        assertInvalid("remark must be a string",Map.of(
                "progressType","PHONE","progressAt","2026-07-31T10:00:00","remark",7),
                91L,7001L);
        assertInvalid("remark must not exceed 1000 characters",Map.of(
                "progressType","PHONE","progressAt","2026-07-31T10:00:00",
                "remark","x".repeat(1001)),91L,7001L);
    }

    private void assertInvalid(String message,Map<String,Object> payload,Long leadId,Long todoId)
    {
        PayloadValidationException error=assertThrows(PayloadValidationException.class,
                ()->LeadProgressPayloadParser.parse(payload,leadId,todoId));
        assertEquals("TODO_HANDLER_PAYLOAD_INVALID",error.getBusinessCode());
        assertEquals(message,error.getMessage());
    }
}
