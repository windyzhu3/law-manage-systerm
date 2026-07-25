package com.law.business.lead.outbound;

public record OutboundCallRequest(Long leadId,Long todoId,String destination,String businessOccurrenceKey) { }
