package com.ruoyi.system.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadSetting;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.service.IBizCustomerService;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class BizCustomerServiceImpl implements IBizCustomerService
{
    private static final String CUSTOMER_NORMAL = "0";
    private static final String CUSTOMER_MERGED = "2";
    private static final String CUSTOMER_MODULE_PERMISSIONS =
            "customer:list,customer:query,customer:add,customer:edit,customer:remove,customer:import,customer:export,"
                    + "customer:contact:list,customer:contact:add,customer:contact:edit,customer:contact:remove,"
                    + "customer:followup:list,customer:followup:add,customer:followup:remove,"
                    + "customer:tag:list,customer:tag:add,customer:tag:edit,customer:tag:remove,customer:tag:assign,"
                    + "customer:merge:list,customer:merge:merge";

    @Autowired
    private BizCustomerMapper customerMapper;

    @Autowired
    private BizLeadMapper leadMapper;

    @Autowired
    private BizContractMapper contractMapper;

    @Autowired
    private ISysDictTypeService dictTypeService;

    @Override
    @DataScope(deptAlias = "c", userAlias = "c", userField = "owner_id")
    public List<BizCustomer> selectCustomerList(BizCustomer customer)
    {
        return customerMapper.selectCustomerList(customer);
    }

    @Override
    public BizCustomer selectCustomerById(Long customerId)
    {
        return requireActiveCustomer(customerId);
    }

    @Override
    @Transactional
    public BizCustomer convertLeadToCustomer(BizLead lead)
    {
        BizCustomer existed = customerMapper.selectCustomerByLeadId(lead.getLeadId());
        if (existed != null)
        {
            return requireActiveCustomer(existed.getCustomerId());
        }
        String name = StringUtils.isNotEmpty(lead.getCompanyName()) ? lead.getCompanyName() : (StringUtils.isNotEmpty(lead.getContactName()) ? lead.getContactName() : lead.getLeadName());
        BizCustomer duplicate = customerMapper.selectDuplicateCustomerInScope(lead.getMobile(), null, name, SecurityUtils.getUserId(), SecurityUtils.getDeptId(), !SecurityUtils.isAdmin(), CUSTOMER_MODULE_PERMISSIONS);
        if (duplicate != null)
        {
            insertLeadContactIfAbsent(duplicate.getCustomerId(), lead, name);
            return duplicate;
        }
        BizCustomer customer = new BizCustomer();
        customer.setCustomerNo("KH" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        customer.setCustomerName(name);
        customer.setCustomerType(dictValue("law_customer_type", StringUtils.isNotEmpty(lead.getCompanyName()) ? "enterprise" : "personal"));
        customer.setMobile(lead.getMobile());
        customer.setWechat(lead.getWechat());
        customer.setCompanyName(lead.getCompanyName());
        customer.setSourceCode(lead.getSourceCode());
        customer.setCustomerLevel(dictValue("law_customer_level", lead.getPriority()));
        customer.setMainDemand(lead.getLegalDemand());
        customer.setOwnerId(lead.getOwnerId());
        customer.setDeptId(lead.getDeptId());
        customer.setLeadId(lead.getLeadId());
        customer.setCreateBy(SecurityUtils.getUsername());
        normalizeNewCustomer(customer);
        validateCustomer(customer);
        int rows = customerMapper.insertCustomer(customer);
        assertRowsChanged(rows, "Customer was not created");
        insertLeadContactIfAbsent(customer.getCustomerId(), lead, name);
        return customer;
    }

    @Override
    public int insertCustomer(BizCustomer customer)
    {
        normalizeNewCustomer(customer);
        validateCustomer(customer);
        customer.setCreateBy(SecurityUtils.getUsername());
        customer.setCustomerNo("KH" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        if (customer.getOwnerId() == null)
        {
            customer.setOwnerId(SecurityUtils.getUserId());
            customer.setDeptId(SecurityUtils.getDeptId());
        }
        int rows = customerMapper.insertCustomer(customer);
        assertRowsChanged(rows, "Customer was not created");
        return rows;
    }

    @Override
    public int updateCustomer(BizCustomer customer)
    {
        requireActiveCustomer(customer.getCustomerId());
        normalizeCustomerUpdate(customer);
        validateCustomer(customer);
        customer.setStatus(null);
        customer.setUpdateBy(SecurityUtils.getUsername());
        int rows = customerMapper.updateCustomer(customer);
        assertRowsChanged(rows, "Customer was changed, please refresh and try again");
        return rows;
    }

    @Override
    public int deleteCustomerByIds(Long[] customerIds)
    {
        for (Long customerId : customerIds)
        {
            requireActiveCustomer(customerId);
            if (customerMapper.countContractsByCustomerId(customerId) > 0)
            {
                throw new ServiceException("客户已关联合同，不允许删除，请先处理合同或执行客户合并");
            }
        }
        int rows = customerMapper.deleteCustomerByIds(customerIds, SecurityUtils.getUsername());
        assertRowsChanged(rows, "Customer was changed, please refresh and try again");
        return rows;
    }

    @Override
    @Transactional
    public String importCustomer(List<BizCustomer> customerList, Boolean updateSupport, String operName)
    {
        if (StringUtils.isNull(customerList) || customerList.isEmpty())
        {
            throw new ServiceException("导入客户数据不能为空");
        }
        int successNum = 0;
        for (BizCustomer customer : customerList)
        {
            if (StringUtils.isEmpty(customer.getCustomerName()))
            {
                continue;
            }
            customer.setCreateBy(operName);
            if (StringUtils.isEmpty(customer.getCustomerType()))
            {
                customer.setCustomerType(dictValue("law_customer_type", "personal"));
            }
            if (customer.getOwnerId() == null)
            {
                customer.setOwnerId(SecurityUtils.getUserId());
                customer.setDeptId(SecurityUtils.getDeptId());
            }
            BizCustomer duplicate = customerMapper.selectDuplicateCustomerInScope(customer.getMobile(), customer.getCreditCode(), customer.getCustomerName(), SecurityUtils.getUserId(), SecurityUtils.getDeptId(), !SecurityUtils.isAdmin(), CUSTOMER_MODULE_PERMISSIONS);
            if (duplicate != null)
            {
                if (!Boolean.TRUE.equals(updateSupport))
                {
                    throw new ServiceException("客户已存在，勾选更新后可覆盖导入：" + customer.getCustomerName());
                }
                customer.setCustomerId(duplicate.getCustomerId());
                updateCustomer(customer);
            }
            else
            {
                insertCustomer(customer);
            }
            successNum++;
        }
        return "导入成功，共 " + successNum + " 条";
    }

    @Override
    public Map<String, Object> selectDashboard()
    {
        Map<String, Object> data = new HashMap<>();
        Boolean dataScope = !SecurityUtils.isAdmin();
        data.put("cards", customerMapper.selectDashboardCards(SecurityUtils.getUserId(), SecurityUtils.getDeptId(), dataScope, CUSTOMER_MODULE_PERMISSIONS, Collections.emptyMap()));
        data.put("types", customerMapper.selectTypeStats(SecurityUtils.getUserId(), SecurityUtils.getDeptId(), dataScope, CUSTOMER_MODULE_PERMISSIONS, Collections.emptyMap()));
        return data;
    }

    @Override public List<Map<String, Object>> selectContacts(Map<String, Object> params) { applyDataScope(params); return customerMapper.selectContacts(params); }
    @Override public int insertContact(Map<String, Object> contact) { requireOperableCustomer(toLong(contact.get("customerId"))); validateContact(contact); contact.put("createBy", SecurityUtils.getUsername()); int rows = customerMapper.insertContact(contact); assertRowsChanged(rows, "Contact was not created"); return rows; }
    @Override public int updateContact(Map<String, Object> contact) { Long customerId = customerMapper.selectContactCustomerId(toLong(contact.get("contactId"))); requireOperableCustomer(customerId); validateContact(contact); contact.put("customerId", customerId); contact.put("updateBy", SecurityUtils.getUsername()); int rows = customerMapper.updateContact(contact); assertRowsChanged(rows, "Contact was changed, please refresh and try again"); return rows; }
    @Override public int deleteContact(Long contactId) { requireOperableCustomer(customerMapper.selectContactCustomerId(contactId)); int rows = customerMapper.deleteContact(contactId, SecurityUtils.getUsername()); assertRowsChanged(rows, "Contact was changed, please refresh and try again"); return rows; }
    @Override public List<Map<String, Object>> selectFollowups(Map<String, Object> params) { applyDataScope(params); return customerMapper.selectFollowups(params); }
    @Override public int insertFollowup(Map<String, Object> followup) { requireOperableCustomer(toLong(followup.get("customerId"))); validateFollowup(followup); followup.put("followUserId", SecurityUtils.getUserId()); followup.put("createBy", SecurityUtils.getUsername()); int rows = customerMapper.insertFollowup(followup); assertRowsChanged(rows, "Followup was not created"); assertRowsChanged(customerMapper.touchCustomerFollowTime(followup), "Customer follow time was not updated"); return rows; }
    @Override public int deleteFollowup(Long followupId) { requireOperableCustomer(customerMapper.selectFollowupCustomerId(followupId)); int rows = customerMapper.deleteFollowup(followupId, SecurityUtils.getUsername()); assertRowsChanged(rows, "Followup was changed, please refresh and try again"); return rows; }
    @Override public List<Map<String, Object>> selectTags(Map<String, Object> params) { return customerMapper.selectTags(params); }
    @Override public int insertTag(Map<String, Object> tag) { validateTag(tag); tag.put("createBy", SecurityUtils.getUsername()); int rows = customerMapper.insertTag(tag); assertRowsChanged(rows, "Customer tag was not created"); return rows; }
    @Override public int updateTag(Map<String, Object> tag) { validateTag(tag); tag.put("updateBy", SecurityUtils.getUsername()); int rows = customerMapper.updateTag(tag); assertRowsChanged(rows, "Customer tag was changed, please refresh and try again"); return rows; }
    @Override public int deleteTag(Long tagId) { customerMapper.deleteTagRelations(tagId); int rows = customerMapper.deleteTag(tagId); assertRowsChanged(rows, "Customer tag was changed, please refresh and try again"); return rows; }
    @Override public List<Long> selectCustomerTagIds(Long customerId) { requireActiveCustomer(customerId); return customerMapper.selectCustomerTagIds(customerId); }

    @Override
    @Transactional
    public int setCustomerTags(Long customerId, Long[] tagIds)
    {
        requireOperableCustomer(customerId);
        customerMapper.deleteCustomerTags(customerId);
        Set<Long> uniqueTagIds = new LinkedHashSet<>();
        if (tagIds != null)
        {
            for (Long tagId : tagIds)
            {
                if (tagId == null)
                {
                    continue;
                }
                uniqueTagIds.add(tagId);
            }
        }
        for (Long tagId : uniqueTagIds)
        {
            if (customerMapper.countEnabledTag(tagId) == 0)
            {
                throw new ServiceException("客户标签不存在或已停用");
            }
            assertRowsChanged(customerMapper.insertCustomerTag(customerId, tagId), "Customer tag relation was not created");
        }
        return 1;
    }

    @Override
    @DataScope(deptAlias = "c", userAlias = "c", userField = "owner_id")
    public List<Map<String, Object>> selectMergeCandidates(BizCustomer customer) { return customerMapper.selectMergeCandidates(customer); }
    @Override public List<Map<String, Object>> selectMergeLogs(Map<String, Object> params) { applyDataScope(params); return customerMapper.selectMergeLogs(params); }

    @Override
    @Transactional
    public int mergeCustomer(Long mainCustomerId, Long mergedCustomerId, String content)
    {
        if (mainCustomerId.equals(mergedCustomerId))
        {
            throw new ServiceException("主客户和待合并客户不能相同");
        }
        BizCustomer mainCustomer = requireOperableCustomer(mainCustomerId);
        BizCustomer mergedCustomer = requireOperableCustomer(mergedCustomerId);
        List<Long> affectedContractIds = customerMapper.selectContractIdsByCustomerId(mergedCustomerId);
        String mergeContent = StringUtils.isEmpty(content) || "null".equalsIgnoreCase(content) ? "手工合并" : content;
        customerMapper.moveContacts(mergedCustomerId, mainCustomerId, SecurityUtils.getUsername());
        customerMapper.moveFollowups(mergedCustomerId, mainCustomerId, SecurityUtils.getUsername());
        customerMapper.moveTagRelations(mergedCustomerId, mainCustomerId);
        customerMapper.deleteCustomerTags(mergedCustomerId);
        int movedContracts = customerMapper.moveContracts(mergedCustomerId, mainCustomerId, SecurityUtils.getUsername());
        if (!affectedContractIds.isEmpty())
        {
            assertRowsChanged(movedContracts, "Customer contracts were not moved");
        }
        int rows = customerMapper.markMerged(mergedCustomerId, SecurityUtils.getUsername());
        assertRowsChanged(rows, "Customer was changed, please refresh and try again");
        assertRowsChanged(customerMapper.insertMergeLog(mainCustomerId, mergedCustomerId, mergeContent, SecurityUtils.getUsername()), "Customer merge log was not created");
        for (Long contractId : affectedContractIds)
        {
            assertRowsChanged(contractMapper.insertStatusLog(contractId, null, null, "customer_merge", limitText("客户合并迁移: " + mergedCustomer.getCustomerName() + " -> " + mainCustomer.getCustomerName() + "；" + mergeContent, 500), SecurityUtils.getUsername()), "Contract status log was not created");
        }
        return 1;
    }

    private void applyDataScope(BizCustomer customer)
    {
        customer.setCurrentUserId(SecurityUtils.getUserId());
        customer.setCurrentDeptId(SecurityUtils.getDeptId());
        customer.setDataScope(!SecurityUtils.isAdmin());
    }

    private void applyDataScope(Map<String, Object> params)
    {
        params.put("currentUserId", SecurityUtils.getUserId());
        params.put("currentDeptId", SecurityUtils.getDeptId());
        params.put("dataScope", !SecurityUtils.isAdmin());
        params.put("permissions", CUSTOMER_MODULE_PERMISSIONS);
    }

    private Long toLong(Object value)
    {
        if (value == null || StringUtils.isEmpty(String.valueOf(value)))
        {
            throw new ServiceException("请选择客户");
        }
        return Long.valueOf(String.valueOf(value));
    }

    private void insertLeadContactIfAbsent(Long customerId, BizLead lead, String fallbackName)
    {
        String contactName = StringUtils.isNotEmpty(lead.getContactName()) ? lead.getContactName() : fallbackName;
        if (StringUtils.isEmpty(contactName))
        {
            return;
        }
        if (customerMapper.countContactByCustomerAndMobileOrName(customerId, lead.getMobile(), contactName) > 0)
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
        contact.put("createBy", SecurityUtils.getUsername());
        assertRowsChanged(customerMapper.insertContact(contact), "Contact was not created");
    }

    private String dictValue(String dictType, String preferredValue)
    {
        List<SysDictData> options = dictTypeService.selectDictDataByType(dictType);
        if (options != null)
        {
            for (SysDictData item : options)
            {
                if (preferredValue.equals(item.getDictValue()))
                {
                    return item.getDictValue();
                }
            }
            for (SysDictData item : options)
            {
                if (item.getDefault())
                {
                    return item.getDictValue();
                }
            }
        }
        return preferredValue;
    }

    private void validateContact(Map<String, Object> contact)
    {
        requiredText(contact.get("contactName"), "联系人不能为空");
        requiredText(contact.get("mobile"), "联系人手机号不能为空");
        requiredText(contact.get("relationType"), "联系人关系不能为空");
        if (StringUtils.isEmpty(text(contact.get("keyContact"))))
        {
            contact.put("keyContact", dictValue("law_yes_no_flag", "0"));
        }
        assertDictValue("law_contact_relation", contact.get("relationType"), "联系人关系不合法");
        assertDictValue("law_yes_no_flag", contact.get("keyContact"), "关键联系人标记不合法");
    }

    private void validateCustomer(BizCustomer customer)
    {
        if (customer == null)
        {
            throw new ServiceException("客户不能为空");
        }
        requiredText(customer.getCustomerName(), "客户名称不能为空");
        requiredText(customer.getCustomerType(), "客户类型不能为空");
        requiredText(customer.getMobile(), "手机号不能为空");
        requiredText(customer.getSourceCode(), "客户来源不能为空");
        requiredText(customer.getCustomerLevel(), "客户等级不能为空");
        requiredText(customer.getMainDemand(), "主要需求不能为空");
        if (customer.getOwnerId() == null)
        {
            throw new ServiceException("负责人不能为空");
        }
        if ("enterprise".equals(customer.getCustomerType()))
        {
            requiredText(customer.getCompanyName(), "企业客户公司名称不能为空");
        }
        assertDictValue("law_customer_type", customer.getCustomerType(), "客户类型不合法");
        assertDictValue("law_customer_level", customer.getCustomerLevel(), "客户等级不合法");
        assertDictValue("law_customer_industry", customer.getIndustry(), "客户行业不合法");
        assertEnabledLeadSetting("source", customer.getSourceCode(), "客户来源不存在或已停用");
    }

    private void normalizeNewCustomer(BizCustomer customer)
    {
        if (customer == null)
        {
            return;
        }
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
        if (customer == null)
        {
            return;
        }
        if (StringUtils.isEmpty(customer.getCustomerLevel()))
        {
            customer.setCustomerLevel(null);
        }
        if (StringUtils.isEmpty(customer.getIndustry()))
        {
            customer.setIndustry(null);
        }
        if (StringUtils.isEmpty(customer.getWechat()))
        {
            customer.setWechat(null);
        }
        if (StringUtils.isEmpty(customer.getEmail()))
        {
            customer.setEmail(null);
        }
        if (StringUtils.isEmpty(customer.getCompanyName()))
        {
            customer.setCompanyName(null);
        }
        if (StringUtils.isEmpty(customer.getCreditCode()))
        {
            customer.setCreditCode(null);
        }
        if (StringUtils.isEmpty(customer.getRegion()))
        {
            customer.setRegion(null);
        }
        if (StringUtils.isEmpty(customer.getSourceCode()))
        {
            customer.setSourceCode(null);
        }
        if (StringUtils.isEmpty(customer.getMainDemand()))
        {
            customer.setMainDemand(null);
        }
        if (StringUtils.isEmpty(customer.getRemark()))
        {
            customer.setRemark(null);
        }
    }

    private void validateFollowup(Map<String, Object> followup)
    {
        requiredText(followup.get("followType"), "跟进方式不能为空");
        requiredText(followup.get("content"), "跟进内容不能为空");
        assertDictValue("law_customer_follow_type", followup.get("followType"), "跟进方式不合法");
    }

    private void validateTag(Map<String, Object> tag)
    {
        requiredText(tag.get("tagName"), "标签名称不能为空");
        if (StringUtils.isEmpty(text(tag.get("status"))))
        {
            tag.put("status", "0");
        }
        assertDictValue("sys_normal_disable", tag.get("status"), "标签状态不合法");
        if (tag.get("orderNum") == null || StringUtils.isEmpty(String.valueOf(tag.get("orderNum"))))
        {
            tag.put("orderNum", 0);
        }
    }

    private void assertDictValue(String dictType, Object value, String message)
    {
        String valueText = text(value);
        if (StringUtils.isEmpty(valueText))
        {
            return;
        }
        List<SysDictData> options = dictTypeService.selectDictDataByType(dictType);
        if (options == null || options.isEmpty())
        {
            throw new ServiceException("字典未初始化：" + dictType);
        }
        for (SysDictData item : options)
        {
            if (valueText.equals(item.getDictValue()))
            {
                return;
            }
        }
        throw new ServiceException(message);
    }

    private void assertEnabledLeadSetting(String settingType, Object value, String message)
    {
        String valueText = requiredText(value, message);
        BizLeadSetting query = new BizLeadSetting();
        query.setSettingType(settingType);
        query.setSettingCode(valueText);
        query.setStatus("0");
        List<BizLeadSetting> settings = leadMapper.selectSettingList(query);
        if (settings == null || settings.isEmpty())
        {
            throw new ServiceException(message);
        }
    }

    private String requiredText(Object value, String message)
    {
        String text = text(value);
        if (StringUtils.isEmpty(text))
        {
            throw new ServiceException(message);
        }
        return text;
    }

    private String text(Object value)
    {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String limitText(String value, int maxLength)
    {
        if (StringUtils.isEmpty(value) || value.length() <= maxLength)
        {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void assertRowsChanged(int rows, String message)
    {
        if (rows <= 0)
        {
            throw new ServiceException(message);
        }
    }

    private void assertCustomerAccess(Long customerId)
    {
        if (customerId == null)
        {
            throw new ServiceException("Customer does not exist or has been deleted");
        }
        if (SecurityUtils.isAdmin())
        {
            return;
        }
        if (customerMapper.countCustomerInDataScope(customerId, SecurityUtils.getUserId(), SecurityUtils.getDeptId(), CUSTOMER_MODULE_PERMISSIONS) == 0)
        {
            throw new ServiceException("No permission to access this customer");
        }
    }

    private BizCustomer requireActiveCustomer(Long customerId)
    {
        if (customerId == null)
        {
            throw new ServiceException("Customer does not exist or has been deleted");
        }
        BizCustomer customer = customerMapper.selectCustomerById(customerId);
        if (customer == null || "2".equals(customer.getDelFlag()) || CUSTOMER_MERGED.equals(customer.getStatus()))
        {
            throw new ServiceException("Customer does not exist or has been deleted");
        }
        assertCustomerAccess(customerId);
        return customer;
    }

    private BizCustomer requireOperableCustomer(Long customerId)
    {
        BizCustomer customer = requireActiveCustomer(customerId);
        if (!CUSTOMER_NORMAL.equals(customer.getStatus()))
        {
            throw new ServiceException("当前客户状态不允许继续经营操作");
        }
        return customer;
    }
}
