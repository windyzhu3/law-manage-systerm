package com.ruoyi.system.service.customer;

import static com.ruoyi.system.service.customer.CustomerAccessPolicy.DATA_SCOPE_PERMISSIONS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.customer.dto.CustomerContactCreateCommand;
import com.law.business.customer.dto.CustomerContactUpdateCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class CustomerContactService
{
    private final BizCustomerMapper mapper;
    private final CustomerAccessPolicy access;
    private final BusinessActorProvider actors;
    private final ISysDictTypeService dictionaries;

    public CustomerContactService(BizCustomerMapper mapper, CustomerAccessPolicy access,
            BusinessActorProvider actors, ISysDictTypeService dictionaries)
    {
        this.mapper = mapper;
        this.access = access;
        this.actors = actors;
        this.dictionaries = dictionaries;
    }

    public List<Map<String, Object>> list(Map<String, Object> params)
    {
        applyScope(params);
        return mapper.selectContacts(params);
    }

    @Transactional
    public int create(CustomerContactCreateCommand command)
    {
        requireCreateCommand(command);
        access.requireOperable(command.getCustomerId());
        BusinessActor actor = actors.current();
        Map<String, Object> contact = toMap(command);
        normalizeAndValidate(contact);
        contact.put("createBy", actor.userName());
        changed(mapper.insertContact(contact), "联系人创建失败");
        clearOtherPrimaryContacts(contact, actor.userName());
        return 1;
    }

    @Transactional
    public int update(CustomerContactUpdateCommand command)
    {
        if (command == null || command.getContactId() == null)
        {
            throw error(BusinessErrorCode.VALIDATION_FAILED, "联系人不能为空");
        }
        Long storedCustomerId = mapper.selectContactCustomerId(command.getContactId());
        if (storedCustomerId == null)
        {
            throw error(BusinessErrorCode.DATA_NOT_FOUND, "联系人不存在");
        }
        if (command.getCustomerId() != null && !storedCustomerId.equals(command.getCustomerId()))
        {
            throw error(BusinessErrorCode.ACCESS_DENIED, "联系人不能迁移到其他客户");
        }
        access.requireOperable(storedCustomerId);
        BusinessActor actor = actors.current();
        Map<String, Object> contact = toMap(command);
        contact.put("customerId", storedCustomerId);
        normalizeAndValidate(contact);
        contact.put("updateBy", actor.userName());
        changed(mapper.updateContact(contact), "联系人已变化，请刷新后重试");
        clearOtherPrimaryContacts(contact, actor.userName());
        return 1;
    }

    @Transactional
    public int delete(Long contactId)
    {
        if (contactId == null) throw error(BusinessErrorCode.VALIDATION_FAILED, "联系人不能为空");
        Long customerId = mapper.selectContactCustomerId(contactId);
        if (customerId == null) throw error(BusinessErrorCode.DATA_NOT_FOUND, "联系人不存在");
        access.requireOperable(customerId);
        changed(mapper.deleteContact(contactId, actors.current().userName()), "联系人已变化，请刷新后重试");
        return 1;
    }

    private void clearOtherPrimaryContacts(Map<String, Object> contact, String operator)
    {
        if (!"1".equals(contact.get("keyContact"))) return;
        mapper.clearOtherKeyContacts((Long) contact.get("customerId"), toLong(contact.get("contactId")),
                dictValue("law_yes_no_flag", "0"), operator);
    }

    private void normalizeAndValidate(Map<String, Object> contact)
    {
        required(contact.get("contactName"), "联系人不能为空");
        required(contact.get("mobile"), "联系人手机号不能为空");
        requireDict("law_contact_relation", required(contact.get("relationType"), "联系人关系不能为空"),
                "联系人关系不合法");
        if (StringUtils.isEmpty(text(contact.get("keyContact"))))
        {
            contact.put("keyContact", dictValue("law_yes_no_flag", "0"));
        }
        requireDict("law_yes_no_flag", text(contact.get("keyContact")), "关键联系人标记不合法");
    }

    private Map<String, Object> toMap(CustomerContactCreateCommand command)
    {
        Map<String, Object> value = new HashMap<>();
        value.put("customerId", command.getCustomerId());
        value.put("contactName", command.getContactName());
        value.put("positionName", command.getPositionName());
        value.put("mobile", command.getMobile());
        value.put("wechat", command.getWechat());
        value.put("email", command.getEmail());
        value.put("relationType", command.getRelationType());
        value.put("keyContact", command.getKeyContact());
        value.put("ownerId", command.getOwnerId());
        value.put("remark", command.getRemark());
        return value;
    }

    private Map<String, Object> toMap(CustomerContactUpdateCommand command)
    {
        Map<String, Object> value = new HashMap<>();
        value.put("contactId", command.getContactId());
        value.put("customerId", command.getCustomerId());
        value.put("contactName", command.getContactName());
        value.put("positionName", command.getPositionName());
        value.put("mobile", command.getMobile());
        value.put("wechat", command.getWechat());
        value.put("email", command.getEmail());
        value.put("relationType", command.getRelationType());
        value.put("keyContact", command.getKeyContact());
        value.put("ownerId", command.getOwnerId());
        value.put("remark", command.getRemark());
        return value;
    }

    private void requireCreateCommand(CustomerContactCreateCommand command)
    {
        if (command == null || command.getCustomerId() == null)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "客户不能为空");
    }

    private void applyScope(Map<String, Object> params)
    {
        BusinessActor actor = actors.current();
        params.put("currentUserId", actor.userId());
        params.put("currentDeptId", actor.deptId());
        params.put("dataScope", !actor.administrator());
        params.put("permissions", DATA_SCOPE_PERMISSIONS);
    }

    private void requireDict(String type, String value, String message)
    {
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        if (options != null && options.stream().anyMatch(item -> value.equals(item.getDictValue()))) return;
        throw error(options == null || options.isEmpty() ? BusinessErrorCode.PRECONDITION_FAILED
                : BusinessErrorCode.VALIDATION_FAILED,
                options == null || options.isEmpty() ? "字典未初始化：" + type : message);
    }

    private String dictValue(String type, String preferred)
    {
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        if (options != null)
        {
            for (SysDictData item : options) if (preferred.equals(item.getDictValue())) return item.getDictValue();
            for (SysDictData item : options) if (item.getDefault()) return item.getDictValue();
        }
        return preferred;
    }

    private String required(Object value, String message)
    {
        String text = text(value);
        if (StringUtils.isEmpty(text)) throw error(BusinessErrorCode.VALIDATION_FAILED, message);
        return text;
    }

    private String text(Object value) { return value == null ? null : String.valueOf(value).trim(); }
    private Long toLong(Object value) { return value == null ? null : Long.valueOf(String.valueOf(value)); }
    private void changed(int rows, String message) { if (rows <= 0) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, message); }
    private ServiceException error(BusinessErrorCode code, String message) { return new ServiceException(message, code.name()); }
}
