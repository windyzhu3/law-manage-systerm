package com.ruoyi.system.service.customer;

import static com.ruoyi.system.service.customer.CustomerAccessPolicy.DATA_SCOPE_PERMISSIONS;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadSetting;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class CustomerCommandService
{
    private final BizCustomerMapper mapper;
    private final BizLeadMapper leads;
    private final ISysDictTypeService dictionaries;
    private final BusinessActorProvider actors;
    private final CustomerAccessPolicy access;

    public CustomerCommandService(BizCustomerMapper mapper, BizLeadMapper leads,
            ISysDictTypeService dictionaries, BusinessActorProvider actors, CustomerAccessPolicy access)
    {
        this.mapper = mapper;
        this.leads = leads;
        this.dictionaries = dictionaries;
        this.actors = actors;
        this.access = access;
    }

    @Transactional
    public int create(BizCustomer customer)
    {
        if (customer == null)
        {
            throw error(BusinessErrorCode.VALIDATION_FAILED, "客户不能为空");
        }
        BusinessActor actor = actors.current();
        normalizeNewCustomer(customer);
        if (customer.getOwnerId() == null)
        {
            customer.setOwnerId(actor.userId());
            customer.setDeptId(actor.deptId());
        }
        required(customer.getCustomerName(), "客户名称不能为空");
        required(customer.getMobile(), "手机号不能为空");
        rejectDuplicate(customer, actor);
        validateCustomer(customer);
        customer.setCustomerNo(nextCustomerNo());
        customer.setCreateBy(actor.userName());
        int rows = mapper.insertCustomer(customer);
        changed(rows, "客户创建失败");
        return rows;
    }

    @Transactional
    public int update(BizCustomer customer)
    {
        if (customer == null || customer.getCustomerId() == null)
        {
            throw error(BusinessErrorCode.VALIDATION_FAILED, "客户不能为空");
        }
        access.requireOperable(customer.getCustomerId());
        normalizeCustomerUpdate(customer);
        validateCustomer(customer);
        customer.setStatus(null);
        customer.setUpdateBy(actors.current().userName());
        int rows = mapper.updateCustomer(customer);
        changed(rows, "客户已变化，请刷新后重试");
        return rows;
    }

    @Transactional
    public int delete(Long[] customerIds)
    {
        if (customerIds == null || customerIds.length == 0)
        {
            throw error(BusinessErrorCode.VALIDATION_FAILED, "请选择客户");
        }
        for (Long customerId : customerIds)
        {
            access.requireOperable(customerId);
            if (mapper.countContractsByCustomerId(customerId) > 0)
            {
                throw error(BusinessErrorCode.PRECONDITION_FAILED,
                        "客户已关联合同，不允许删除，请先处理合同或执行客户合并");
            }
        }
        int rows = mapper.deleteCustomerByIds(customerIds, actors.current().userName());
        changed(rows, "客户已变化，请刷新后重试");
        return rows;
    }

    @Transactional
    public String importCustomers(List<BizCustomer> customers, Boolean updateSupport, String operatorName)
    {
        if (customers == null || customers.isEmpty())
        {
            throw error(BusinessErrorCode.VALIDATION_FAILED, "导入客户数据不能为空");
        }
        int success = 0;
        BusinessActor actor = actors.current();
        for (BizCustomer customer : customers)
        {
            if (customer == null || StringUtils.isEmpty(customer.getCustomerName()))
            {
                continue;
            }
            normalizeNewCustomer(customer);
            if (customer.getOwnerId() == null)
            {
                customer.setOwnerId(actor.userId());
                customer.setDeptId(actor.deptId());
            }
            BizCustomer duplicate = findDuplicate(customer, actor);
            if (duplicate != null)
            {
                if (!Boolean.TRUE.equals(updateSupport))
                {
                    throw error(BusinessErrorCode.PRECONDITION_FAILED,
                            "客户已存在，勾选更新后可覆盖导入：" + customer.getCustomerName());
                }
                customer.setCustomerId(duplicate.getCustomerId());
                update(customer);
            }
            else
            {
                validateCustomer(customer);
                customer.setCustomerNo(nextCustomerNo());
                customer.setCreateBy(operatorName);
                changed(mapper.insertCustomer(customer), "客户创建失败");
            }
            success++;
        }
        return "导入成功，共 " + success + " 条";
    }

    @Transactional
    public Long createFromLead(BizLead lead)
    {
        if (lead == null || lead.getLeadId() == null)
        {
            throw error(BusinessErrorCode.VALIDATION_FAILED, "来源线索不能为空");
        }
        BizCustomer existing = mapper.selectCustomerByLeadId(lead.getLeadId());
        if (existing != null)
        {
            return access.requireReadable(existing.getCustomerId()).getCustomerId();
        }
        BusinessActor actor = actors.current();
        String name = customerName(lead);
        BizCustomer duplicate = mapper.selectDuplicateCustomerInScope(lead.getMobile(), null, name,
                actor.userId(), actor.deptId(), !actor.administrator(), DATA_SCOPE_PERMISSIONS);
        if (duplicate != null)
        {
            insertLeadContactIfAbsent(duplicate.getCustomerId(), lead, name, actor);
            return duplicate.getCustomerId();
        }

        BizCustomer customer = customerFromLead(lead, name, actor);
        validateCustomer(customer);
        changed(mapper.insertCustomer(customer), "客户创建失败");
        if (customer.getCustomerId() == null)
        {
            throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, "客户编号生成失败");
        }
        insertLeadContactIfAbsent(customer.getCustomerId(), lead, name, actor);
        return customer.getCustomerId();
    }

    private BizCustomer customerFromLead(BizLead lead, String name, BusinessActor actor)
    {
        BizCustomer customer = new BizCustomer();
        customer.setCustomerNo(nextCustomerNo());
        customer.setCustomerName(name);
        customer.setCustomerType(dictValue("law_customer_type",
                StringUtils.isNotEmpty(lead.getCompanyName()) ? "enterprise" : "personal"));
        customer.setMobile(lead.getMobile());
        customer.setWechat(lead.getWechat());
        customer.setCompanyName(lead.getCompanyName());
        customer.setSourceCode(lead.getSourceCode());
        customer.setCustomerLevel(dictValue("law_customer_level", lead.getPriority()));
        customer.setMainDemand(lead.getLegalDemand());
        customer.setOwnerId(lead.getOwnerId());
        customer.setDeptId(lead.getDeptId());
        customer.setLeadId(lead.getLeadId());
        customer.setCreateBy(actor.userName());
        normalizeNewCustomer(customer);
        return customer;
    }

    private void insertLeadContactIfAbsent(Long customerId, BizLead lead, String fallbackName,
            BusinessActor actor)
    {
        String contactName = StringUtils.isNotEmpty(lead.getContactName()) ? lead.getContactName() : fallbackName;
        if (StringUtils.isEmpty(contactName)
                || mapper.countContactByCustomerAndMobileOrName(customerId, lead.getMobile(), contactName) > 0)
        {
            return;
        }
        Map<String, Object> contact = new HashMap<>();
        contact.put("customerId", customerId);
        contact.put("contactName", contactName);
        contact.put("mobile", lead.getMobile());
        contact.put("wechat", lead.getWechat());
        contact.put("relationType", dictValue("law_contact_relation", "daily"));
        contact.put("keyContact", dictValue("law_yes_no_flag", "1"));
        contact.put("ownerId", lead.getOwnerId());
        contact.put("createBy", actor.userName());
        changed(mapper.insertContact(contact), "联系人创建失败");
    }

    private void rejectDuplicate(BizCustomer customer, BusinessActor actor)
    {
        if (findDuplicate(customer, actor) != null)
        {
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "同一数据范围内已存在相同客户");
        }
    }

    private BizCustomer findDuplicate(BizCustomer customer, BusinessActor actor)
    {
        return mapper.selectDuplicateCustomerInScope(customer.getMobile(), customer.getCreditCode(),
                customer.getCustomerName(), actor.userId(), actor.deptId(), !actor.administrator(),
                DATA_SCOPE_PERMISSIONS);
    }

    private void validateCustomer(BizCustomer customer)
    {
        required(customer.getCustomerName(), "客户名称不能为空");
        required(customer.getCustomerType(), "客户类型不能为空");
        required(customer.getMobile(), "手机号不能为空");
        required(customer.getSourceCode(), "客户来源不能为空");
        required(customer.getCustomerLevel(), "客户等级不能为空");
        required(customer.getMainDemand(), "主要需求不能为空");
        if (customer.getOwnerId() == null)
        {
            throw error(BusinessErrorCode.VALIDATION_FAILED, "负责人不能为空");
        }
        if ("enterprise".equals(customer.getCustomerType()))
        {
            required(customer.getCompanyName(), "企业客户公司名称不能为空");
        }
        requireDict("law_customer_type", customer.getCustomerType(), "客户类型不合法");
        requireDict("law_customer_level", customer.getCustomerLevel(), "客户等级不合法");
        if (StringUtils.isNotEmpty(customer.getIndustry()))
        {
            requireDict("law_customer_industry", customer.getIndustry(), "客户行业不合法");
        }
        requireEnabledLeadSetting("source", customer.getSourceCode(), "客户来源不存在或已停用");
    }

    private void normalizeNewCustomer(BizCustomer customer)
    {
        if (StringUtils.isEmpty(customer.getCustomerType()))
        {
            customer.setCustomerType(dictValue("law_customer_type", "personal"));
        }
        if (StringUtils.isEmpty(customer.getCustomerLevel()))
        {
            customer.setCustomerLevel(dictValue("law_customer_level", "2"));
        }
    }

    private void normalizeCustomerUpdate(BizCustomer customer)
    {
        if (StringUtils.isEmpty(customer.getCustomerLevel())) customer.setCustomerLevel(null);
        if (StringUtils.isEmpty(customer.getIndustry())) customer.setIndustry(null);
        if (StringUtils.isEmpty(customer.getWechat())) customer.setWechat(null);
        if (StringUtils.isEmpty(customer.getEmail())) customer.setEmail(null);
        if (StringUtils.isEmpty(customer.getCompanyName())) customer.setCompanyName(null);
        if (StringUtils.isEmpty(customer.getCreditCode())) customer.setCreditCode(null);
        if (StringUtils.isEmpty(customer.getRegion())) customer.setRegion(null);
        if (StringUtils.isEmpty(customer.getSourceCode())) customer.setSourceCode(null);
        if (StringUtils.isEmpty(customer.getMainDemand())) customer.setMainDemand(null);
        if (StringUtils.isEmpty(customer.getRemark())) customer.setRemark(null);
    }

    private void requireEnabledLeadSetting(String type, String value, String message)
    {
        BizLeadSetting query = new BizLeadSetting();
        query.setSettingType(type);
        query.setSettingCode(value);
        query.setStatus("0");
        List<BizLeadSetting> settings = leads.selectSettingList(query);
        if (settings == null || settings.isEmpty())
        {
            throw error(BusinessErrorCode.PRECONDITION_FAILED, message);
        }
    }

    private void requireDict(String type, String value, String message)
    {
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        if (options != null && options.stream().anyMatch(item -> value.equals(item.getDictValue())))
        {
            return;
        }
        throw error(options == null || options.isEmpty() ? BusinessErrorCode.PRECONDITION_FAILED
                : BusinessErrorCode.VALIDATION_FAILED,
                options == null || options.isEmpty() ? "字典未初始化：" + type : message);
    }

    private String dictValue(String type, String preferred)
    {
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        if (options != null)
        {
            for (SysDictData option : options)
            {
                if (preferred != null && preferred.equals(option.getDictValue())) return option.getDictValue();
            }
            for (SysDictData option : options)
            {
                if (option.getDefault()) return option.getDictValue();
            }
        }
        return preferred;
    }

    private String customerName(BizLead lead)
    {
        if (StringUtils.isNotEmpty(lead.getCompanyName())) return lead.getCompanyName();
        if (StringUtils.isNotEmpty(lead.getContactName())) return lead.getContactName();
        return required(lead.getLeadName(), "线索名称不能为空");
    }

    private String nextCustomerNo()
    {
        return "KH" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    private String required(Object value, String message)
    {
        String text = value == null ? null : String.valueOf(value).trim();
        if (StringUtils.isEmpty(text))
        {
            throw error(BusinessErrorCode.VALIDATION_FAILED, message);
        }
        return text;
    }

    private void changed(int rows, String message)
    {
        if (rows <= 0)
        {
            throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, message);
        }
    }

    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
}
