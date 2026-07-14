package com.ruoyi.system.service.customer;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.customer.dto.CustomerTagAssignCommand;
import com.law.business.customer.dto.CustomerTagCreateCommand;
import com.law.business.customer.dto.CustomerTagUpdateCommand;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class CustomerTagService
{
    private final BizCustomerMapper mapper;
    private final CustomerAccessPolicy access;
    private final BusinessActorProvider actors;
    private final ISysDictTypeService dictionaries;

    public CustomerTagService(BizCustomerMapper mapper, CustomerAccessPolicy access,
            BusinessActorProvider actors, ISysDictTypeService dictionaries)
    {
        this.mapper = mapper;
        this.access = access;
        this.actors = actors;
        this.dictionaries = dictionaries;
    }

    public List<Map<String, Object>> list(Map<String, Object> params) { return mapper.selectTags(params); }

    public int create(CustomerTagCreateCommand command)
    {
        validate(command == null ? null : command.getTagName(), command == null ? null : command.getStatus());
        Map<String, Object> tag = toMap(command);
        tag.put("createBy", actors.current().userName());
        changed(mapper.insertTag(tag), "客户标签创建失败");
        return 1;
    }

    public int update(CustomerTagUpdateCommand command)
    {
        if (command == null || command.getTagId() == null)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "标签不能为空");
        validate(command.getTagName(), command.getStatus());
        Map<String, Object> tag = toMap(command);
        tag.put("updateBy", actors.current().userName());
        changed(mapper.updateTag(tag), "客户标签已变化，请刷新后重试");
        return 1;
    }

    @Transactional
    public int delete(Long tagId)
    {
        if (tagId == null) throw error(BusinessErrorCode.VALIDATION_FAILED, "标签不能为空");
        mapper.deleteTagRelations(tagId);
        changed(mapper.deleteTag(tagId), "客户标签已变化，请刷新后重试");
        return 1;
    }

    public List<Long> customerTags(Long customerId)
    {
        access.requireReadable(customerId);
        return mapper.selectCustomerTagIds(customerId);
    }

    @Transactional
    public int assign(CustomerTagAssignCommand command)
    {
        if (command == null || command.getCustomerId() == null || command.getTagIds() == null)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "客户和标签集合不能为空");
        access.requireOperable(command.getCustomerId());
        Set<Long> requested = new LinkedHashSet<>();
        for (Long tagId : command.getTagIds()) if (tagId != null) requested.add(tagId);
        for (Long tagId : requested)
        {
            if (mapper.countEnabledTag(tagId) == 0)
                throw error(BusinessErrorCode.PRECONDITION_FAILED, "客户标签不存在或已停用");
        }
        List<Long> stored = mapper.selectCustomerTagIds(command.getCustomerId());
        Set<Long> current = stored == null ? new LinkedHashSet<>() : new LinkedHashSet<>(stored);
        if (current.equals(requested)) return 1;
        mapper.deleteCustomerTags(command.getCustomerId());
        for (Long tagId : requested)
            changed(mapper.insertCustomerTag(command.getCustomerId(), tagId), "客户标签关系创建失败");
        return 1;
    }

    private void validate(String tagName, String status)
    {
        if (StringUtils.isEmpty(tagName == null ? null : tagName.trim()))
            throw error(BusinessErrorCode.VALIDATION_FAILED, "标签名称不能为空");
        requireDict("sys_normal_disable", status, "标签状态不合法");
    }

    private void requireDict(String type, String value, String message)
    {
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        if (options != null && value != null && options.stream().anyMatch(item -> value.equals(item.getDictValue()))) return;
        throw error(options == null || options.isEmpty() ? BusinessErrorCode.PRECONDITION_FAILED
                : BusinessErrorCode.VALIDATION_FAILED,
                options == null || options.isEmpty() ? "字典未初始化：" + type : message);
    }

    private Map<String, Object> toMap(CustomerTagCreateCommand command)
    {
        Map<String, Object> tag = new HashMap<>();
        tag.put("tagName", command.getTagName()); tag.put("tagColor", command.getTagColor());
        tag.put("orderNum", command.getOrderNum() == null ? 0 : command.getOrderNum());
        tag.put("status", command.getStatus()); tag.put("remark", command.getRemark());
        return tag;
    }

    private Map<String, Object> toMap(CustomerTagUpdateCommand command)
    {
        Map<String, Object> tag = new HashMap<>();
        tag.put("tagId", command.getTagId()); tag.put("tagName", command.getTagName());
        tag.put("tagColor", command.getTagColor()); tag.put("orderNum", command.getOrderNum() == null ? 0 : command.getOrderNum());
        tag.put("status", command.getStatus()); tag.put("remark", command.getRemark());
        return tag;
    }

    private void changed(int rows, String message) { if (rows <= 0) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, message); }
    private ServiceException error(BusinessErrorCode code, String message) { return new ServiceException(message, code.name()); }
}
