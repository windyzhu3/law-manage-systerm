package com.law.business.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ExpenseUpdateCommand
{
    @NotNull(message = "请选择费用")
    private Long expenseId;
    @NotBlank(message = "请选择费用类型")
    private String expenseType;
    @NotNull(message = "请输入费用金额")
    @DecimalMin(value = "0.01", message = "费用金额必须大于0")
    private BigDecimal amount;
    @NotNull(message = "请选择发生日期")
    private LocalDate occurDate;
    @NotBlank(message = "请选择付款状态")
    private String payStatus;
    @NotBlank(message = "请选择报销状态")
    private String reimburseStatus;
    @NotBlank(message = "请选择凭证状态")
    private String voucherStatus;
    private String remark;

    public Map<String, Object> toPersistenceMap()
    {
        Map<String, Object> values = new HashMap<>();
        values.put("expenseId", expenseId);
        values.put("expenseType", expenseType);
        values.put("amount", amount);
        values.put("occurDate", occurDate);
        values.put("payStatus", payStatus);
        values.put("reimburseStatus", reimburseStatus);
        values.put("voucherStatus", voucherStatus);
        values.put("remark", remark);
        return values;
    }

    public Long getExpenseId() { return expenseId; }
    public void setExpenseId(Long expenseId) { this.expenseId = expenseId; }
    public String getExpenseType() { return expenseType; }
    public void setExpenseType(String expenseType) { this.expenseType = expenseType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getOccurDate() { return occurDate; }
    public void setOccurDate(LocalDate occurDate) { this.occurDate = occurDate; }
    public String getPayStatus() { return payStatus; }
    public void setPayStatus(String payStatus) { this.payStatus = payStatus; }
    public String getReimburseStatus() { return reimburseStatus; }
    public void setReimburseStatus(String reimburseStatus) { this.reimburseStatus = reimburseStatus; }
    public String getVoucherStatus() { return voucherStatus; }
    public void setVoucherStatus(String voucherStatus) { this.voucherStatus = voucherStatus; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
