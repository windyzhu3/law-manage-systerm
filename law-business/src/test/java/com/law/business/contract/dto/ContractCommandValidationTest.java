package com.law.business.contract.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class ContractCommandValidationTest
{
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createRequiresCustomerNameCaseAndPositiveAmount()
    {
        ContractCreateCommand command = new ContractCreateCommand();
        command.setSignAmount(BigDecimal.ZERO);

        assertTrue(fields(command).containsAll(Set.of("contractName", "customerId", "caseType", "signAmount")));
    }

    @Test
    void feePlanUsesCurrentFrontendFieldNames()
    {
        ContractFeePlanCreateCommand command = new ContractFeePlanCreateCommand();

        assertTrue(fields(command).containsAll(Set.of("contractId", "periodNo", "receivableAmount", "planReceiveDate")));
    }

    @Test
    void templateAndAttachmentRequireFileMetadata()
    {
        assertTrue(fields(new ContractTemplateCreateCommand())
                .containsAll(Set.of("templateName", "caseType", "fileName", "fileUrl")));
        assertTrue(fields(new ContractAttachmentCreateCommand())
                .containsAll(Set.of("contractId", "fileName", "fileUrl", "fileType")));
    }

    @Test
    void numberRuleUsesCurrentFrontendFieldNames()
    {
        assertTrue(fields(new ContractNumberRuleUpdateCommand())
                .containsAll(Set.of("ruleId", "ruleName", "prefix", "datePattern", "serialLength", "status")));
    }

    @Test
    void updateCommandsDoNotExposeLifecycleStatusFields()
    {
        Set<String> names = Set.of(java.util.Arrays.stream(ContractUpdateCommand.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).toArray(String[]::new));

        assertFalse(names.contains("auditStatus"));
        assertFalse(names.contains("contractStatus"));
        assertFalse(names.contains("signStatus"));
    }

    @Test
    void lifecycleCommandsRequireOpinionReasonAndAllowedStatuses()
    {
        ContractApprovalCommand approval = new ContractApprovalCommand();
        approval.setContractId(10L);
        approval.setAction("pass");
        assertTrue(fields(approval).contains("opinion"));

        ContractReasonCommand reason = new ContractReasonCommand();
        reason.setContractId(10L);
        assertTrue(fields(reason).contains("reason"));

        ContractSignCommand sign = new ContractSignCommand();
        sign.setContractId(10L);
        sign.setSignStatus("unsupported");
        assertTrue(fields(sign).contains("signStatus"));

        FeeInvoiceCommand invoice = new FeeInvoiceCommand();
        invoice.setPlanId(21L);
        invoice.setInvoiceStatus("unsupported");
        assertTrue(fields(invoice).contains("invoiceStatus"));
    }

    private Set<String> fields(Object command)
    {
        return validator.validate(command).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
