package com.ruoyi.system.service.impl;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.service.IBizCustomerService;
import com.ruoyi.system.service.customer.CustomerCommandService;
import com.ruoyi.system.service.customer.CustomerQueryService;
import com.ruoyi.system.service.customer.CustomerContactService;
import com.ruoyi.system.service.customer.CustomerFollowupService;
import com.ruoyi.system.service.customer.CustomerMergeService;
import com.ruoyi.system.service.customer.CustomerTagService;
import com.law.business.customer.dto.CustomerContactCreateCommand;
import com.law.business.customer.dto.CustomerContactUpdateCommand;
import com.law.business.customer.dto.CustomerFollowupCreateCommand;
import com.law.business.customer.dto.CustomerMergeCommand;
import com.law.business.customer.dto.CustomerTagAssignCommand;
import com.law.business.customer.dto.CustomerTagCreateCommand;
import com.law.business.customer.dto.CustomerTagUpdateCommand;

@Service
public class BizCustomerServiceImpl implements IBizCustomerService
{
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

    @Autowired
    private CustomerMergeService customerMergeService;

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

    @Override public List<Map<String, Object>> selectMergeCandidates(BizCustomer customer) { return customerMergeService.candidates(customer); }
    @Override public List<Map<String, Object>> selectMergeLogs(Map<String, Object> params) { return customerMergeService.logs(params); }
    @Override public int mergeCustomer(CustomerMergeCommand command) { return customerMergeService.merge(command); }
}
