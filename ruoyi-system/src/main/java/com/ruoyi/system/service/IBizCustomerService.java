package com.ruoyi.system.service;

import java.util.List;
import java.util.Map;
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
    int insertContact(Map<String, Object> contact);
    int updateContact(Map<String, Object> contact);
    int deleteContact(Long contactId);
    List<Map<String, Object>> selectFollowups(Map<String, Object> params);
    int insertFollowup(Map<String, Object> followup);
    int deleteFollowup(Long followupId);
    List<Map<String, Object>> selectTags(Map<String, Object> params);
    int insertTag(Map<String, Object> tag);
    int updateTag(Map<String, Object> tag);
    int deleteTag(Long tagId);
    List<Long> selectCustomerTagIds(Long customerId);
    int setCustomerTags(Long customerId, Long[] tagIds);
    List<Map<String, Object>> selectMergeCandidates(BizCustomer customer);
    List<Map<String, Object>> selectMergeLogs(Map<String, Object> params);
    int mergeCustomer(Long mainCustomerId, Long mergedCustomerId, String content);
}
