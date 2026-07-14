package com.ruoyi.system.service.impl;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.service.IBizCustomerService;
import com.ruoyi.system.service.customer.CustomerCommandService;
import com.ruoyi.system.service.customer.CustomerQueryService;
import com.ruoyi.system.service.customer.CustomerContactService;
import com.ruoyi.system.service.customer.CustomerFollowupService;
import com.ruoyi.system.service.customer.CustomerTagService;
import com.law.business.customer.dto.CustomerContactCreateCommand;
import com.law.business.customer.dto.CustomerContactUpdateCommand;
import com.law.business.customer.dto.CustomerFollowupCreateCommand;
import com.law.business.customer.dto.CustomerTagAssignCommand;
import com.law.business.customer.dto.CustomerTagCreateCommand;
import com.law.business.customer.dto.CustomerTagUpdateCommand;

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
    private CustomerQueryService customerQueryService;

    @Autowired
    private CustomerCommandService customerCommandService;

    @Autowired
    private CustomerContactService customerContactService;

    @Autowired
    private CustomerFollowupService customerFollowupService;

    @Autowired
    private CustomerTagService customerTagService;

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

    @Override public List<Map<String, Object>> selectContacts(Map<String, Object> params) { return customerContactService.list(params); }
    @Override public int insertContact(CustomerContactCreateCommand contact) { return customerContactService.create(contact); }
    @Override public int updateContact(CustomerContactUpdateCommand contact) { return customerContactService.update(contact); }
    @Override public int deleteContact(Long contactId) { return customerContactService.delete(contactId); }
    @Override public List<Map<String, Object>> selectFollowups(Map<String, Object> params) { return customerFollowupService.list(params); }
    @Override public int insertFollowup(CustomerFollowupCreateCommand followup) { return customerFollowupService.create(followup); }
    @Override public int deleteFollowup(Long followupId) { return customerFollowupService.delete(followupId); }
    @Override public List<Map<String, Object>> selectTags(Map<String, Object> params) { return customerTagService.list(params); }
    @Override public int insertTag(CustomerTagCreateCommand tag) { return customerTagService.create(tag); }
    @Override public int updateTag(CustomerTagUpdateCommand tag) { return customerTagService.update(tag); }
    @Override public int deleteTag(Long tagId) { return customerTagService.delete(tagId); }
    @Override public List<Long> selectCustomerTagIds(Long customerId) { return customerTagService.customerTags(customerId); }

    @Override
    @Transactional
    public int setCustomerTags(CustomerTagAssignCommand command)
    {
        return customerTagService.assign(command);
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

    private void applyDataScope(Map<String, Object> params)
    {
        params.put("currentUserId", SecurityUtils.getUserId());
        params.put("currentDeptId", SecurityUtils.getDeptId());
        params.put("dataScope", !SecurityUtils.isAdmin());
        params.put("permissions", CUSTOMER_MODULE_PERMISSIONS);
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
