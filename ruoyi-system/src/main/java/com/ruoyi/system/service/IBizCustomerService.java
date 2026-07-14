package com.ruoyi.system.service;

import java.util.List;
import java.util.Map;
import com.law.business.customer.dto.CustomerContactCreateCommand;
import com.law.business.customer.dto.CustomerContactUpdateCommand;
import com.law.business.customer.dto.CustomerFollowupCreateCommand;
import com.law.business.customer.dto.CustomerMergeCommand;
import com.law.business.customer.dto.CustomerTagAssignCommand;
import com.law.business.customer.dto.CustomerTagCreateCommand;
import com.law.business.customer.dto.CustomerTagUpdateCommand;
import com.ruoyi.system.domain.BizCustomer;

public interface IBizCustomerService
{
    List<BizCustomer> selectCustomerList(BizCustomer customer);
    BizCustomer selectCustomerById(Long customerId);
    int insertCustomer(BizCustomer customer);
    int updateCustomer(BizCustomer customer);
    int deleteCustomerByIds(Long[] customerIds);
    String importCustomer(List<BizCustomer> customerList, Boolean updateSupport, String operName);
    Map<String, Object> selectDashboard();
    List<Map<String, Object>> selectContacts(Map<String, Object> params);
    int insertContact(CustomerContactCreateCommand contact);
    int updateContact(CustomerContactUpdateCommand contact);
    int deleteContact(Long contactId);
    List<Map<String, Object>> selectFollowups(Map<String, Object> params);
    int insertFollowup(CustomerFollowupCreateCommand followup);
    int deleteFollowup(Long followupId);
    List<Map<String, Object>> selectTags(Map<String, Object> params);
    int insertTag(CustomerTagCreateCommand tag);
    int updateTag(CustomerTagUpdateCommand tag);
    int deleteTag(Long tagId);
    List<Long> selectCustomerTagIds(Long customerId);
    int setCustomerTags(CustomerTagAssignCommand command);
    List<Map<String, Object>> selectMergeCandidates(BizCustomer customer);
    List<Map<String, Object>> selectMergeLogs(Map<String, Object> params);
    int mergeCustomer(CustomerMergeCommand command);
}
