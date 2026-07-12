package com.law.business.contract.dto;

import java.time.LocalDate;

public record ContractTodoSignCommand(Long contractId,String signStatus,String signMethod,LocalDate signDate,String signFileUrl) {}
