package com.ruoyi.system.service.todo;

import org.springframework.stereotype.Component;
import com.law.todo.spi.TodoBusinessAccessChecker;
import com.law.file.spi.FileBusinessAccessChecker;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.BizMatterMapper;
import com.ruoyi.system.mapper.BizCustomerMapper;

@Component
public class RuoYiTodoBusinessAccessChecker implements TodoBusinessAccessChecker,FileBusinessAccessChecker
{
    private static final String CONTRACT_PERMISSIONS="contract:list,contract:query";
    private static final String CUSTOMER_PERMISSIONS="customer:list,customer:query";
    private static final String CASE_PERMISSIONS="case:list,case:query";
    private static final String MATTER_PERMISSIONS="matter:list,matter:query,matter:mine:list,matter:mine:query";
    private final BizLeadMapper leads;private final BizCustomerMapper customers;private final BizContractMapper contracts;private final BizCaseMapper cases;private final BizMatterMapper matters;

    public RuoYiTodoBusinessAccessChecker(BizLeadMapper leads,BizCustomerMapper customers,BizContractMapper contracts,BizCaseMapper cases,BizMatterMapper matters)
    {this.leads=leads;this.customers=customers;this.contracts=contracts;this.cases=cases;this.matters=matters;}

    @Override public boolean supports(String type){return "LEAD".equals(type)||"CUSTOMER".equals(type)||"CONTRACT".equals(type)||"CASE".equals(type)||"MATTER".equals(type);}

    @Override public boolean canView(String type,Long id,Long userId,Long deptId)
    {
        if(Long.valueOf(1L).equals(userId)) return true;
        return switch(type){
            case "LEAD" -> leads.countLeadInDataScope(id,userId,deptId,false)>0;
            case "CUSTOMER" -> customers.countCustomerInDataScope(id,userId,deptId,CUSTOMER_PERMISSIONS)>0;
            case "CONTRACT" -> contracts.countContractInDataScope(id,userId,deptId,CONTRACT_PERMISSIONS)>0;
            case "CASE" -> cases.countCaseInDataScope(id,userId,deptId,true,CASE_PERMISSIONS)>0;
            case "MATTER" -> matters.countMatterInDataScope(id,userId,deptId,true,MATTER_PERMISSIONS)>0;
            default -> false;
        };
    }
    @Override public boolean canRead(String type,Long id,Long userId,Long deptId){return canView(type,id,userId,deptId);}
}
