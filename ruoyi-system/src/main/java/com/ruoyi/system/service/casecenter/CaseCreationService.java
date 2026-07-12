package com.ruoyi.system.service.casecenter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.law.business.shared.status.CaseStatus;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.domain.SysNotice;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ISysNoticeService;

/** Creates the initial case aggregate after a contract is signed. */
@Service
public class CaseCreationService
{
    @Autowired
    private BizCaseMapper caseMapper;

    @Autowired
    private ISysDictTypeService dictTypeService;

    @Autowired
    private ISysNoticeService noticeService;

    @Autowired
    private BusinessEventPublisher eventPublisher;

    @Transactional
    public int createFromContract(BizContract contract)
    {
        if (contract == null || contract.getContractId() == null)
        {
            return 0;
        }
        if (caseMapper.selectCaseByContractId(contract.getContractId()) != null)
        {
            return 0;
        }

        Map<String, Object> entity = new HashMap<>();
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
        entity.put("createBy", SecurityUtils.getUsername());

        assertRowsChanged(caseMapper.insertCase(entity), "案件创建失败");
        Long caseId = requiredLong(entity.get("caseId"), "案件创建失败");
        insertStatusLog(caseId);
        createNotice(contract);
        publishCreated(caseId, String.valueOf(entity.get("caseNo")), contract.getContractId());
        return 1;
    }

    private void insertStatusLog(Long caseId)
    {
        Map<String, Object> log = new HashMap<>();
        log.put("caseId", caseId);
        log.put("fromStatus", null);
        log.put("toStatus", CaseStatus.PENDING.code());
        log.put("actionType", "create");
        log.put("content", "合同签署后生成待分案案件");
        log.put("createBy", SecurityUtils.getUsername());
        assertRowsChanged(caseMapper.insertStatusLog(log), "案件状态记录创建失败");
    }

    private void createNotice(BizContract contract)
    {
        SysNotice notice = new SysNotice();
        notice.setNoticeTitle("新案件待分配");
        notice.setNoticeType("1");
        notice.setNoticeContent("合同 " + contract.getContractNo() + " 已签署，生成待分案案件：" + contract.getContractName());
        notice.setStatus("0");
        notice.setCreateBy(SecurityUtils.getUsername());
        notice.setRemark("案管中心");
        noticeService.insertNotice(notice);
    }

    private void publishCreated(Long caseId, String caseNo, Long contractId)
    {
        Map<String, Object> payload = new HashMap<>();
        payload.put("contractId", contractId);
        eventPublisher.publish(new BusinessEventCommand(BusinessEventType.CASE_CREATED, "CASE", caseId, caseNo,
                BusinessEventType.CASE_CREATED.name() + ":" + caseId + ":" + IdUtils.fastUUID(), payload));
    }

    private String dictValue(String dictType, String preferredValue)
    {
        List<SysDictData> options = dictTypeService.selectDictDataByType(dictType);
        if (options != null)
        {
            for (SysDictData item : options)
            {
                if (preferredValue != null && preferredValue.equals(item.getDictValue()))
                {
                    return item.getDictValue();
                }
            }
        }
        return dictDefault(dictType, preferredValue);
    }

    private String dictDefault(String dictType, String fallback)
    {
        List<SysDictData> options = dictTypeService.selectDictDataByType(dictType);
        if (options != null)
        {
            for (SysDictData item : options)
            {
                if (item.getDefault())
                {
                    return item.getDictValue();
                }
            }
        }
        return fallback;
    }

    private Long requiredLong(Object value, String message)
    {
        if (value == null)
        {
            throw new ServiceException(message);
        }
        return Long.valueOf(String.valueOf(value));
    }

    private void assertRowsChanged(int rows, String message)
    {
        if (rows <= 0)
        {
            throw new ServiceException(message);
        }
    }
}
