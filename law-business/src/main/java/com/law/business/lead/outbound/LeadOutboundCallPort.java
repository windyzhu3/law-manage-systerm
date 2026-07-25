package com.law.business.lead.outbound;

/**
 * Vendor-neutral outbound-call boundary. Implementations own credentials, request signing,
 * callback authentication, and canonical provider-payload hashing.
 */
public interface LeadOutboundCallPort
{
    String providerCode();
    OutboundCallTicket start(OutboundCallRequest request);
    VerifiedLeadCall verify(OutboundCallCallbackCommand callback);
}
