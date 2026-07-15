package com.law.business.contract.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ContractFeePlanCreateCommand
{
    @NotNull(message = "请选择合同") private Long contractId;
    @NotNull(message = "请输入期数") @Min(value = 1, message = "期数必须大于0") private Integer periodNo;
    @NotNull(message = "请输入应收金额") @DecimalMin(value = "0.01", message = "应收金额必须大于0") private BigDecimal receivableAmount;
    @NotNull(message = "请选择计划收款日") private LocalDate planReceiveDate;
    private Long financeUserId;
    @Size(max = 500) private String remark;

    public Long getContractId(){return contractId;} public void setContractId(Long v){contractId=v;}
    public Integer getPeriodNo(){return periodNo;} public void setPeriodNo(Integer v){periodNo=v;}
    public BigDecimal getReceivableAmount(){return receivableAmount;} public void setReceivableAmount(BigDecimal v){receivableAmount=v;}
    public LocalDate getPlanReceiveDate(){return planReceiveDate;} public void setPlanReceiveDate(LocalDate v){planReceiveDate=v;}
    public Long getFinanceUserId(){return financeUserId;} public void setFinanceUserId(Long v){financeUserId=v;}
    public String getRemark(){return remark;} public void setRemark(String v){remark=v;}
}
