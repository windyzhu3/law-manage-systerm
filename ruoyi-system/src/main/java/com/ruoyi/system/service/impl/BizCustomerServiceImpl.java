package com.ruoyi.system.service.impl;

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
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.service.IBizCustomerService;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.customer.CustomerCommandService;
import com.ruoyi.system.service.customer.CustomerQueryService;

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
    private BizContractMapper contractMapper;

    @Autowired
    private ISysDictTypeService dictTypeService;

    @Autowired
    private CustomerQueryService customerQueryService;

    @Autowired
    private CustomerCommandService customerCommandService;

    @Override
    public List<BizCustomer> selectCustomerList(BizCustomer customer)
    {
        return customerQueryService.list(customer);
    }

    @Override
    public BizCustomer selectCustomerById(Long customerId)
    {
        return customerQueryService.detail(customerId);
    }

    @Override
    public int insertCustomer(BizCustomer customer)
    {
        return customerCommandService.create(customer);
    }

    @Override
    public int updateCustomer(BizCustomer customer)
    {
        return customerCommandService.update(customer);
    }

    @Override
    public int deleteCustomerByIds(Long[] customerIds)
    {
        return customerCommandService.delete(customerIds);
    }

    @Override
    @Transactional
    public String importCustomer(List<BizCustomer> customerList, Boolean updateSupport, String operName)
    {
        return customerCommandService.importCustomers(customerList, updateSupport, operName);
    }

    @Override
    public Map<String, Object> selectDashboard()
    {
        return customerQueryService.dashboard();
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
