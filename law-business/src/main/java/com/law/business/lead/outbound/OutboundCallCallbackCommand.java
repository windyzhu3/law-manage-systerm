package com.law.business.lead.outbound;

public record OutboundCallCallbackCommand(String providerCode,String rawPayload,String signature) { }
