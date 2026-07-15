package com.ruoyi.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.law.business.lawcase.dto.LawyerProfileSaveCommand;
import com.law.business.lawcase.dto.LawyerProfileStatusCommand;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.service.impl.BizCaseServiceImpl;

class CaseFacadeGuardTest
{
    @Test
    void facadeStaysWithinCompatibilityBoundary() throws Exception
    {
        Path source = Paths.get("src", "main", "java", "com", "ruoyi", "system", "service", "impl",
                "BizCaseServiceImpl.java");
        assertTrue(Files.readAllLines(source).size() <= 180, "案件 Facade 不应超过180行");
        Field[] fields = BizCaseServiceImpl.class.getDeclaredFields();
        assertFalse(Arrays.stream(fields).anyMatch(field -> BizCaseMapper.class.isAssignableFrom(field.getType())),
                "案件 Facade 不应直接依赖 Mapper");
    }

    @Test
    void lawyerProfileWritesUseTypedCommands() throws Exception
    {
        Method save = IBizCaseService.class.getMethod("saveLawyerProfile", LawyerProfileSaveCommand.class);
        Method status = IBizCaseService.class.getMethod("updateLawyerProfileStatus", LawyerProfileStatusCommand.class);
        assertEquals(int.class, save.getReturnType());
        assertEquals(int.class, status.getReturnType());
        assertFalse(Arrays.stream(IBizCaseService.class.getMethods())
                .filter(method -> method.getName().equals("saveLawyerProfile")
                        || method.getName().equals("updateLawyerProfileStatus"))
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .anyMatch(Map.class::isAssignableFrom));
    }
}
