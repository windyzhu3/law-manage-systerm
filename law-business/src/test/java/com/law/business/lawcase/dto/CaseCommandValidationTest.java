package com.law.business.lawcase.dto;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class CaseCommandValidationTest
{
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void batchAssignmentRequiresCases()
    {
        CaseBatchAssignmentCommand command = new CaseBatchAssignmentCommand();
        command.setMainLawyerId(12L);
        command.setAssignMethod("manual");
        command.setPriority("medium");
        command.setAssignReason("normal");

        assertTrue(validator.validate(command).stream()
                .anyMatch(violation -> "请选择案件".equals(violation.getMessage())));
    }

    @Test
    void lawyerProfileRequiresLawyerAndAssignmentStatus()
    {
        LawyerProfileSaveCommand save = new LawyerProfileSaveCommand();
        LawyerProfileStatusCommand status = new LawyerProfileStatusCommand();

        assertTrue(validator.validate(save).stream()
                .anyMatch(violation -> "请选择律师".equals(violation.getMessage())));
        assertTrue(validator.validate(status).stream()
                .anyMatch(violation -> "请选择可分案状态".equals(violation.getMessage())));
    }

    @Test
    void writeCommandsDoNotExposePersistenceMaps()
    {
        for (Class<?> type : List.of(CaseAssignmentCommand.class,
                CaseTransferCommand.class, CaseTransferApprovalCommand.class,
                CaseConfirmCommand.class))
        {
            assertTrue(Arrays.stream(type.getDeclaredMethods())
                    .noneMatch(method -> method.getName().equals("toPersistenceMap")), type.getSimpleName());
        }
    }
}
