package com.ruoyi.system.service.lead;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.lead.dto.LeadCallRecordCommand;
import com.law.business.lead.outbound.LeadOutboundCallPort;
import com.law.business.lead.outbound.OutboundCallCallbackCommand;
import com.law.business.lead.outbound.VerifiedLeadCall;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.security.LeadPermissions;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.file.domain.FileObject.FileActor;
import com.law.file.domain.FileObject.FileBusinessRelation;
import com.law.file.security.FileAccessPolicy;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadCallRecord;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.LeadFlowMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class LeadCallRecordService
{
    private final LeadFlowMapper mapper;
    private final BizLeadMapper leads;
    private final LeadAccessPolicy access;
    private final BusinessActorProvider actors;
    private final ISysDictTypeService dictionaries;
    private final FileAccessPolicy files;
    private final LeadPermissionPolicy permissions;
    private final List<LeadOutboundCallPort> outboundPorts;

    public LeadCallRecordService(LeadFlowMapper mapper,BizLeadMapper leads,LeadAccessPolicy access,
            BusinessActorProvider actors,ISysDictTypeService dictionaries,FileAccessPolicy files,
            LeadPermissionPolicy permissions,List<LeadOutboundCallPort> outboundPorts)
    {
        this.mapper = mapper;
        this.leads = leads;
        this.access = access;
        this.actors = actors;
        this.dictionaries = dictionaries;
        this.files = files;
        this.permissions=permissions;
        this.outboundPorts=outboundPorts==null?List.of():List.copyOf(outboundPorts);
    }

    @Transactional
    public CallRecordOutcome record(LeadCallRecordCommand command)
    {
        require(command != null && command.getLeadId() != null, "Lead is required");
        permissions.require(LeadPermissions.CALL_RECORD_ADD);
        BizLead lead = access.requireOperable(command.getLeadId());
        BusinessActor actor=actors.current();
        if(!actor.administrator()&&!actor.userId().equals(lead.getOwnerId()))
            throw error(BusinessErrorCode.ACCESS_DENIED,"Only the lead owner may add a call record");
        requireManual(command);
        return recordCanonical(command,lead,actor,idempotencyKey(command));
    }

    CallRecordOutcome recordForLead(LeadCallRecordCommand command,BizLead lead,BusinessActor actor,
            Long parentTodoId)
    {
        require(command!=null&&parentTodoId!=null&&parentTodoId.equals(command.getTodoId()),
                "Call record source Todo does not match its parent completion");
        requireManual(command);
        return recordCanonical(command,lead,actor,idempotencyKey(command));
    }

    @Transactional
    public CallRecordOutcome recordTrustedCallback(OutboundCallCallbackCommand callback)
    {
        require(callback!=null&&callback.providerCode()!=null&&!callback.providerCode().isBlank(),
                "Outbound provider identity is required");
        LeadOutboundCallPort port=outboundPorts.stream()
                .filter(value->callback.providerCode().trim().equals(value.providerCode()))
                .findFirst().orElseThrow(()->error(BusinessErrorCode.ACCESS_DENIED,
                        "Outbound provider is not trusted"));
        VerifiedLeadCall verified=port.verify(callback);
        require(verified!=null&&verified.leadId()!=null&&verified.todoId()!=null,
                "Trusted callback did not return a canonical call");
        require("APP".equals(verified.channel())||"OUTBOUND_SYSTEM".equals(verified.channel()),
                "Trusted callback channel is invalid");
        require(verified.externalCallId()!=null&&!verified.externalCallId().isBlank(),
                "Trusted callback external call ID is required");
        require(verified.providerSummaryHash()!=null
                && verified.providerSummaryHash().matches("(?i)[0-9a-f]{64}"),
                "Trusted callback summary hash is invalid");
        BizLead lead=leads.selectLeadById(verified.leadId());
        require(lead!=null&&"0".equals(lead.getDelFlag())&&"ACTIVE".equals(lead.getDisposition()),
                "Trusted callback lead is not active");
        LeadCallRecordCommand command=callbackCommand(verified);
        BusinessActor actor=new BusinessActor(0L,providerActor(port.providerCode()),
                providerActor(port.providerCode()),null,false);
        return recordCanonical(command,lead,actor,"LEAD_CALL:"+verified.channel()+":"
                +port.providerCode()+":"+verified.externalCallId().trim());
    }

    private CallRecordOutcome recordCanonical(LeadCallRecordCommand command,BizLead lead,
            BusinessActor actor,String key)
    {
        validate(command,lead,actor);
        BizLeadCallRecord record = toRecord(command, actor, key);
        int inserted = mapper.insertCallRecordIfAbsent(record);
        if (inserted == 1)
        {
            require(record.getCallRecordId() != null, "Call record identity was not generated");
            return new CallRecordOutcome(record.getCallRecordId(), false);
        }
        BizLeadCallRecord existing = mapper.selectCallRecordByIdempotencyKey(key);
        if (!sameCanonical(record,existing))
            throw error(BusinessErrorCode.DUPLICATE_OPERATION, "LEAD_CALL_RECORD_DUPLICATE");
        return new CallRecordOutcome(existing.getCallRecordId(), true);
    }

    private void validate(LeadCallRecordCommand command, BizLead lead, BusinessActor actor)
    {
        require(command != null && command.getLeadId() != null && command.getLeadId().equals(lead.getLeadId()),
                "Call record does not match lead");
        require(command.getStartedAt() != null, "Call start time is required");
        require(command.getEndedAt() == null || !command.getEndedAt().isBefore(command.getStartedAt()),
                "Call end time is invalid");
        require(command.getDurationSeconds() == null || command.getDurationSeconds() >= 0,
                "Call duration is invalid");
        requireDict("law_call_channel", command.getCallChannel());
        require(command.getExternalCallId() != null && !command.getExternalCallId().isBlank()
                || command.getBusinessOccurrenceKey() != null && !command.getBusinessOccurrenceKey().isBlank(),
                "External call ID or business occurrence key is required");
        if (command.getRecordingFileObjectId() != null)
        {
            List<FileBusinessRelation> relations = files.requireCanRead(command.getRecordingFileObjectId(),
                    new FileActor(actor.userId(), actor.userName(), actor.deptId()));
            boolean related = relations.stream().anyMatch(value -> "LEAD".equals(value.businessType())
                    && lead.getLeadId().equals(value.businessId()));
            require(related, "Recording file is not related to this lead");
        }
    }

    private void requireManual(LeadCallRecordCommand command)
    {
        if(command==null||!"MANUAL".equals(trim(command.getCallChannel()))
                ||trim(command.getProviderSummaryHash())!=null)
            throw error(BusinessErrorCode.ACCESS_DENIED,
                    "APP and OUTBOUND_SYSTEM calls require a trusted provider callback");
    }

    private LeadCallRecordCommand callbackCommand(VerifiedLeadCall value)
    {
        LeadCallRecordCommand command=new LeadCallRecordCommand();
        command.setLeadId(value.leadId());command.setTodoId(value.todoId());
        command.setCallChannel(value.channel());command.setExternalCallId(value.externalCallId());
        command.setStartedAt(value.startedAt());command.setEndedAt(value.endedAt());
        command.setDurationSeconds(value.durationSeconds());command.setCallResult(value.callResult());
        command.setRecordingFileObjectId(value.recordingFileObjectId());
        command.setManualNotes(value.providerNotes());
        command.setProviderSummaryHash(value.providerSummaryHash());
        return command;
    }

    private boolean sameCanonical(BizLeadCallRecord expected,BizLeadCallRecord actual)
    {
        return actual!=null&&Objects.equals(expected.getLeadId(),actual.getLeadId())
                &&Objects.equals(expected.getTodoId(),actual.getTodoId())
                &&Objects.equals(expected.getCallChannel(),actual.getCallChannel())
                &&Objects.equals(expected.getExternalCallId(),actual.getExternalCallId())
                &&Objects.equals(expected.getStartedAt(),actual.getStartedAt())
                &&Objects.equals(expected.getEndedAt(),actual.getEndedAt())
                &&Objects.equals(expected.getDurationSeconds(),actual.getDurationSeconds())
                &&Objects.equals(expected.getCallResult(),actual.getCallResult())
                &&Objects.equals(expected.getRecordingFileObjectId(),actual.getRecordingFileObjectId())
                &&Objects.equals(expected.getManualNotes(),actual.getManualNotes())
                &&Objects.equals(expected.getProviderSummaryHash(),actual.getProviderSummaryHash())
                &&Objects.equals(expected.getIdempotencyKey(),actual.getIdempotencyKey());
    }

    private BizLeadCallRecord toRecord(LeadCallRecordCommand command, BusinessActor actor, String key)
    {
        BizLeadCallRecord value = new BizLeadCallRecord();
        value.setLeadId(command.getLeadId());
        value.setTodoId(command.getTodoId());
        value.setCallChannel(trim(command.getCallChannel()));
        value.setExternalCallId(trim(command.getExternalCallId()));
        value.setStartedAt(canonicalTimestamp(command.getStartedAt()));
        value.setEndedAt(canonicalTimestamp(command.getEndedAt()));
        value.setDurationSeconds(command.getDurationSeconds());
        value.setCallResult(trim(command.getCallResult()));
        value.setRecordingFileObjectId(command.getRecordingFileObjectId());
        value.setManualNotes(trim(command.getManualNotes()));
        value.setProviderSummaryHash(trim(command.getProviderSummaryHash()));
        value.setIdempotencyKey(key);
        value.setCreateBy(actor.userName());
        return value;
    }

    private String idempotencyKey(LeadCallRecordCommand command)
    {
        String identity = command.getExternalCallId() == null || command.getExternalCallId().isBlank()
                ? trim(command.getBusinessOccurrenceKey()) : trim(command.getExternalCallId());
        return "LEAD_CALL:" + trim(command.getCallChannel()) + ":" + identity;
    }

    private void requireDict(String type, String value)
    {
        String expected = trim(value);
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        boolean valid = options != null && options.stream().anyMatch(item -> expected.equals(item.getDictValue()));
        require(valid, "Controlled dictionary value is invalid: " + type);
    }

    private void require(boolean condition, String message)
    {
        if (!condition) throw error(BusinessErrorCode.VALIDATION_FAILED, message);
    }

    private String providerActor(String providerCode)
    {
        String value="outbound:"+(providerCode==null?"unknown":providerCode.trim());
        return value.substring(0,Math.min(64,value.length()));
    }
    private LocalDateTime canonicalTimestamp(LocalDateTime value)
    {
        return value==null?null:value.truncatedTo(ChronoUnit.SECONDS);
    }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }

    public record CallRecordOutcome(Long callRecordId, boolean replayed) { }
}
