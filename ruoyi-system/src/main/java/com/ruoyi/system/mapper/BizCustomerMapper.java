package com.ruoyi.system.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.BizCustomer;

public interface BizCustomerMapper
{
    List<BizCustomer> selectCustomerList(BizCustomer customer);
    BizCustomer selectCustomerById(Long customerId);
    BizCustomer selectCustomerByLeadId(Long leadId);
    BizCustomer selectDuplicateCustomer(@Param("mobile") String mobile, @Param("creditCode") String creditCode, @Param("customerName") String customerName);
    BizCustomer selectDuplicateCustomerInScope(@Param("mobile") String mobile, @Param("creditCode") String creditCode, @Param("customerName") String customerName, @Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId, @Param("dataScope") Boolean dataScope, @Param("permissions") String permissions);
    List<BizCustomer> selectCustomersByExactNameInScope(@Param("customerName") String customerName, @Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId, @Param("dataScope") Boolean dataScope, @Param("permissions") String permissions);
    int insertCustomer(BizCustomer customer);
    int updateCustomer(BizCustomer customer);
    int deleteCustomerByIds(@Param("customerIds") Long[] customerIds, @Param("updateBy") String updateBy);
    int countContractsByCustomerId(Long customerId);
    int countCustomerInDataScope(@Param("customerId") Long customerId, @Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId, @Param("permissions") String permissions);
    List<Map<String, Object>> selectDashboardCards(@Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId, @Param("dataScope") Boolean dataScope, @Param("permissions") String permissions, @Param("params") Map<String, Object> params);
    List<Map<String, Object>> selectTypeStats(@Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId, @Param("dataScope") Boolean dataScope, @Param("permissions") String permissions, @Param("params") Map<String, Object> params);
    List<Map<String, Object>> selectContacts(Map<String, Object> params);
    Long selectContactCustomerId(Long contactId);
    int countContactByCustomerAndMobileOrName(@Param("customerId") Long customerId, @Param("mobile") String mobile, @Param("contactName") String contactName);
    int insertContact(Map<String, Object> contact);
    int updateContact(Map<String, Object> contact);
    int clearOtherKeyContacts(@Param("customerId") Long customerId, @Param("contactId") Long contactId,
            @Param("normalValue") String normalValue, @Param("updateBy") String updateBy);
    int deleteContact(@Param("contactId") Long contactId, @Param("updateBy") String updateBy);
    List<Map<String, Object>> selectFollowups(Map<String, Object> params);
    Long selectFollowupCustomerId(Long followupId);
    int insertFollowup(Map<String, Object> followup);
    int deleteFollowup(@Param("followupId") Long followupId, @Param("updateBy") String updateBy);
    int touchCustomerFollowTime(Map<String, Object> followup);
    List<Map<String, Object>> selectTags(Map<String, Object> params);
    int insertTag(Map<String, Object> tag);
    int updateTag(Map<String, Object> tag);
    int deleteTag(Long tagId);
    int deleteTagRelations(Long tagId);
    int countEnabledTag(Long tagId);
    int deleteCustomerTags(Long customerId);
    int insertCustomerTag(@Param("customerId") Long customerId, @Param("tagId") Long tagId);
    List<Long> selectCustomerTagIds(Long customerId);
    List<Map<String, Object>> selectMergeCandidates(BizCustomer customer);
    List<Map<String, Object>> selectMergeLogs(Map<String, Object> params);
    List<Long> selectContractIdsByCustomerId(Long customerId);
    int moveContacts(@Param("fromId") Long fromId, @Param("toId") Long toId, @Param("updateBy") String updateBy);
    int moveFollowups(@Param("fromId") Long fromId, @Param("toId") Long toId, @Param("updateBy") String updateBy);
    int moveTagRelations(@Param("fromId") Long fromId, @Param("toId") Long toId);
    int moveContracts(@Param("fromId") Long fromId, @Param("toId") Long toId, @Param("updateBy") String updateBy);
    int markMergedConditionally(@Param("fromId") Long fromId, @Param("updateBy") String updateBy,
            @Param("expectedStatus") String expectedStatus);
    int insertMergeLog(@Param("mainId") Long mainId, @Param("mergedId") Long mergedId, @Param("content") String content, @Param("createBy") String createBy);
}
