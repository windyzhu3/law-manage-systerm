package com.ruoyi.system.service.lead;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.lead.dto.LeadCallRecordCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.file.domain.FileObject.FileActor;
import com.law.file.domain.FileObject.FileBusinessRelation;
import com.law.file.security.FileAccessPolicy;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadCallRecord;
import com.ruoyi.system.mapper.LeadFlowMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class LeadCallRecordService
{
    private final LeadFlowMapper mapper;
    private final LeadAccessPolicy access;
    private final BusinessActorProvider actors;
    private final ISysDictTypeService dictionaries;
    private final FileAccessPolicy files;

    public LeadCallRecordService(LeadFlowMapper mapper, LeadAccessPolicy access, BusinessActorProvider actors,
            ISysDictTypeService dictionaries, FileAccessPolicy files)
    {
        this.mapper = mapper;
        this.access = access;
        this.actors = actors;
        this.dictionaries = dictionaries;
        this.files = files;
    }

    @Transactional
    public CallRecordOutcome record(LeadCallRecordCommand command)
    {
        require(command != null && command.getLeadId() != null, "Lead is required");
        BizLead lead = access.requireOperable(command.getLeadId());
        return recordForLead(command, lead, actors.current());
    }

    CallRecordOutcome recordForLead(LeadCallRecordCommand command, BizLead lead, BusinessActor actor)
    {
        validate(command, lead, actor);
        String key = idempotencyKey(command);
        BizLeadCallRecord record = toRecord(command, actor, key);
        int inserted = mapper.insertCallRecordIfAbsent(record);
        if (inserted == 1)
        {
            require(record.getCallRecordId() != null, "Call record identity was not generated");
            return new CallRecordOutcome(record.getCallRecordId(), false);
        }
        BizLeadCallRecord existing = mapper.selectCallRecordByIdempotencyKey(key);
        if (existing == null || !lead.getLeadId().equals(existing.getLeadId())
                || !trim(command.getCallChannel()).equals(existing.getCallChannel())
                || !same(trim(command.getExternalCallId()), existing.getExternalCallId()))
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

    private BizLeadCallRecord toRecord(LeadCallRecordCommand command, BusinessActor actor, String key)
    {
        BizLeadCallRecord value = new BizLeadCallRecord();
        value.setLeadId(command.getLeadId());
        value.setTodoId(command.getTodoId());
        value.setCallChannel(trim(command.getCallChannel()));
        value.setExternalCallId(trim(command.getExternalCallId()));
        value.setStartedAt(command.getStartedAt());
        value.setEndedAt(command.getEndedAt());
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

    private boolean same(String first, String second) { return first == null ? second == null : first.equals(second); }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }

    public record CallRecordOutcome(Long callRecordId, boolean replayed) { }
}
