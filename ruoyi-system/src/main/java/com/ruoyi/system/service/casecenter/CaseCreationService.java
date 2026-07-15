package com.ruoyi.system.service.casecenter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.law.business.security.BusinessActor;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.business.shared.status.CaseStatus;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.domain.SysNotice;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ISysNoticeService;

/** Creates the initial case aggregate after signed and paid contract facts are verified. */
@Service
public class CaseCreationService
{
    private static final String SIGNED = "1";
    private static final String DELETED = "2";

    private final BizCaseMapper cases;
    private final BizContractMapper contracts;
    private final ISysDictTypeService dictionaries;
    private final ISysNoticeService notices;
    private final BusinessEventPublisher events;

    public CaseCreationService(BizCaseMapper cases, BizContractMapper contracts,
            ISysDictTypeService dictionaries, ISysNoticeService notices, BusinessEventPublisher events)
    {
        this.cases = cases;
        this.contracts = contracts;
        this.dictionaries = dictionaries;
        this.notices = notices;
        this.events = events;
    }

    @Transactional
    public int createFromContract(Long contractId)
    {
        return createFromContract(contractId, currentActor());
    }

    @Transactional
    public int createFromContract(Long contractId, BusinessActor actor)
    {
        if (contractId == null) return 0;
        if (exists(contractId)) return 0;
        BizContract contract = contracts.selectContractById(contractId);
        if (contract == null || DELETED.equals(contract.getDelFlag()))
            throw error(BusinessErrorCode.DATA_NOT_FOUND, "合同不存在或已删除");
        if (!SIGNED.equals(contract.getSignStatus()))
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "合同尚未完成签署");
        if (contracts.countConfirmedFeePlans(contractId) <= 0)
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "合同尚无已确认缴费记录");

        Map<String,Object> entity = caseRow(contract, actor);
        try
        {
            changed(cases.insertCase(entity), "案件创建失败");
        }
        catch (DuplicateKeyException duplicate)
        {
            if (exists(contractId)) return 0;
            throw duplicate;
        }
        Long caseId = requiredLong(entity.get("caseId"), "案件创建失败");
        insertStatusLog(caseId, actor);
        createNotice(contract, actor);
        events.publish(new BusinessEventCommand(BusinessEventType.CASE_CREATED, "CASE", caseId,
                String.valueOf(entity.get("caseNo")), "CASE_CREATED:" + caseId,
                Map.of("contractId", contractId)));
        return 1;
    }

    private Map<String,Object> caseRow(BizContract contract, BusinessActor actor)
    {
        Map<String,Object> entity = new HashMap<>();
        entity.put("caseNo", "CS" + contract.getContractNo());
        entity.put("caseName", contract.getContractName());
        entity.put("customerId", contract.getCustomerId());
        entity.put("customerName", contract.getCustomerName());
        entity.put("contractId", contract.getContractId());
        entity.put("contractNo", contract.getContractNo());
        entity.put("caseType", dictValue("law_case_type", contract.getCaseType()));
        entity.put("urgency", dictDefault("law_case_urgency", "normal"));
        entity.put("caseStatus", CaseStatus.PENDING.code());
        entity.put("priority", dictDefault("law_case_priority", "medium"));
        entity.put("estimatedWorkload", BigDecimal.valueOf(24));
        entity.put("ownerId", contract.getOwnerId());
        entity.put("deptId", contract.getDeptId());
        entity.put("createBy", actor.userName());
        return entity;
    }

    private void insertStatusLog(Long caseId, BusinessActor actor)
    {
        Map<String,Object> log = new HashMap<>();
        log.put("caseId", caseId); log.put("fromStatus", null);
        log.put("toStatus", CaseStatus.PENDING.code()); log.put("actionType", "create");
        log.put("content", "合同签署并确认缴费后生成待分案案件"); log.put("createBy", actor.userName());
        changed(cases.insertStatusLog(log), "案件状态记录创建失败");
    }

    private void createNotice(BizContract contract, BusinessActor actor)
    {
        SysNotice notice = new SysNotice();
        notice.setNoticeTitle("新案件待分配"); notice.setNoticeType("1");
        notice.setNoticeContent("合同 " + contract.getContractNo() + " 已完成签署和缴费确认，生成待分案案件：" + contract.getContractName());
        notice.setStatus("0"); notice.setCreateBy(actor.userName()); notice.setRemark("案管中心");
        notices.insertNotice(notice);
    }

    private String dictValue(String type, String preferred)
    {
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        if (options != null) for (SysDictData option : options)
            if (preferred != null && preferred.equals(option.getDictValue())) return option.getDictValue();
        return dictDefault(type, preferred);
    }

    private String dictDefault(String type, String fallback)
    {
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        if (options != null) for (SysDictData option : options) if (option.getDefault()) return option.getDictValue();
        return fallback;
    }

    private Long requiredLong(Object value, String message)
    {
        if (value == null) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, message);
        return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }
    private boolean exists(Long contractId)
    {
        Map<String,Object> existing = cases.selectCaseByContractId(contractId);
        return existing != null && !existing.isEmpty();
    }
    private void changed(int rows, String message)
    {
        if (rows <= 0) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, message);
    }
    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
    private BusinessActor currentActor()
    {
        return new BusinessActor(SecurityUtils.getUserId(), SecurityUtils.getUsername(),
                SecurityUtils.getLoginUser().getUser().getNickName(), SecurityUtils.getDeptId(), SecurityUtils.isAdmin());
    }
}
