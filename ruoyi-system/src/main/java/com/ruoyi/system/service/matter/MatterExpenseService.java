package com.ruoyi.system.service.matter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.mapper.BizMatterMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.law.business.shared.error.BusinessErrorCode;

@Service
public class MatterExpenseService
{
    private static final String PROCESSING="processing";
    private static final String PERMISSIONS="matter:list,matter:query,matter:mine:list,matter:mine:query,matter:expense:list,matter:expense:add,matter:expense:edit,matter:expense:remove";
    private final BizMatterMapper mapper;
    private final ISysDictTypeService dictService;

    public MatterExpenseService(BizMatterMapper mapper,ISysDictTypeService dictService){this.mapper=mapper;this.dictService=dictService;}

    @Transactional
    public int create(Map<String,Object> expense)
    {
        Long caseId=toLong(expense.get("caseId"),"请选择案件"); requireEditable(caseId); validate(expense);
        expense.put("expenseNo","FY"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        expense.put("createBy",SecurityUtils.getUsername()); int rows=mapper.insertExpense(expense); assertRows(rows,"费用创建失败");
        refreshFeeStatus(caseId); log(caseId,"expense_add","新增案件费用："+expense.get("amount")); return rows;
    }

    @Transactional
    public int update(Map<String,Object> expense)
    {
        Map<String,Object> existed=requireExpense(toLong(expense.get("expenseId"),"请选择费用")); Long caseId=toLong(existed.get("case_id"),"请选择案件");
        requireEditable(caseId); validate(expense); expense.put("updateBy",SecurityUtils.getUsername());
        int rows=mapper.updateExpense(expense); assertRows(rows,"费用已变化，请刷新后重试"); refreshFeeStatus(caseId); log(caseId,"expense_edit","编辑案件费用"); return rows;
    }

    @Transactional
    public int delete(Long expenseId)
    {
        Map<String,Object> existed=requireExpense(expenseId); Long caseId=toLong(existed.get("case_id"),"请选择案件"); requireEditable(caseId);
        int rows=mapper.deleteExpense(expenseId,SecurityUtils.getUsername()); assertRows(rows,"费用已变化，请刷新后重试");
        refreshFeeStatus(caseId); log(caseId,"expense_remove","删除案件费用"); return rows;
    }

    private void validate(Map<String,Object> expense)
    {
        required(expense.get("expenseType"),"请选择费用类型"); BigDecimal amount=decimal(required(expense.get("amount"),"请输入费用金额"));
        if(amount.compareTo(BigDecimal.ZERO)<=0) throw error(BusinessErrorCode.VALIDATION_FAILED,"费用金额必须大于0"); required(expense.get("occurDate"),"请选择发生日期");
        assertDict("law_case_expense_type",expense.get("expenseType"),"费用类型不合法"); assertDict("law_case_pay_status",expense.get("payStatus"),"付款状态不合法");
        assertDict("law_case_reimburse_status",expense.get("reimburseStatus"),"报销状态不合法"); assertDict("law_case_voucher_status",expense.get("voucherStatus"),"凭证状态不合法");
    }

    private Map<String,Object> requireExpense(Long id){Map<String,Object> row=mapper.selectExpenseById(id);if(row==null||"2".equals(text(row.get("del_flag"))))throw error(BusinessErrorCode.DATA_NOT_FOUND,"费用不存在");requireAccess(toLong(row.get("case_id"),"请选择案件"));return row;}
    private void requireEditable(Long id){Map<String,Object> matter=requireAccess(id);if(!PROCESSING.equals(text(matter.get("case_status"))))throw error(BusinessErrorCode.STATE_CONFLICT,"只有办理中案件可以发起该操作");}
    private Map<String,Object> requireAccess(Long id){Map<String,Object> matter=mapper.selectMatterById(id);if(matter==null)throw error(BusinessErrorCode.DATA_NOT_FOUND,"案件不存在或已删除");if(!SecurityUtils.isAdmin()&&mapper.countMatterInDataScope(id,SecurityUtils.getUserId(),SecurityUtils.getDeptId(),true,PERMISSIONS)==0)throw error(BusinessErrorCode.ACCESS_DENIED,"无权访问该案件");return matter;}
    private void refreshFeeStatus(Long id){Map<String,Object> value=new HashMap<>();value.put("caseId",id);int count=mapper.countExpenseByCaseId(id);value.put("feeStatus",count==0?"none":(mapper.countUnpaidExpenseByCaseId(id)==0?"settled":"partial"));value.put("updateBy",SecurityUtils.getUsername());mapper.updateMatter(value);}
    private void log(Long id,String action,String content){Map<String,Object> value=new HashMap<>();value.put("caseId",id);value.put("actionType",action);value.put("content",content);value.put("createBy",SecurityUtils.getUsername());assertRows(mapper.insertStatusLog(value),"案件状态记录创建失败");}
    private void assertDict(String type,Object value,String message){String target=text(value);if(StringUtils.isEmpty(target))return;List<SysDictData> options=dictService.selectDictDataByType(type);if(contains(options,target))return;dictService.resetDictCache();options=dictService.selectDictDataByType(type);if(options==null||options.isEmpty())throw new ServiceException("字典未初始化："+type);if(!contains(options,target))throw new ServiceException(message);}
    private boolean contains(List<SysDictData> list,String target){if(list==null)return false;for(SysDictData item:list)if(target.equals(item.getDictValue()))return true;return false;}
    private BigDecimal decimal(String value){try{return new BigDecimal(value);}catch(NumberFormatException e){throw new ServiceException("金额格式不正确");}}
    private Long toLong(Object value,String message){if(value==null||StringUtils.isEmpty(String.valueOf(value)))throw new ServiceException(message);return Long.valueOf(String.valueOf(value));}
    private String required(Object value,String message){String result=text(value);if(StringUtils.isEmpty(result))throw new ServiceException(message);return result;}
    private String text(Object value){return value==null||"null".equalsIgnoreCase(String.valueOf(value))?null:String.valueOf(value).trim();}
    private void assertRows(int rows,String message){if(rows<=0)throw new ServiceException(message);}
    private ServiceException error(BusinessErrorCode code,String message){return new ServiceException(message,code.name());}
}
