package com.ruoyi.system.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.service.impl.BizContractServiceImpl;
import com.ruoyi.system.service.impl.BizCustomerServiceImpl;
import com.ruoyi.system.service.impl.BizLeadServiceImpl;

class LeadCustomerFacadeGuardTest
{
    @Test
    void facadesStayWithinCompatibilityBoundary() throws Exception
    {
        assertTrue(lines("BizLeadServiceImpl.java") <= 150, "线索 Facade 不应超过150行");
        assertTrue(lines("BizCustomerServiceImpl.java") <= 150, "客户 Facade 不应超过150行");
        assertTrue(lines("BizContractServiceImpl.java") <= 150, "合同 Facade 不应超过150行");
    }

    @Test
    void facadesDoNotDependDirectlyOnPersistenceMappers()
    {
        assertNoMapper(BizLeadServiceImpl.class, BizLeadMapper.class);
        assertNoMapper(BizCustomerServiceImpl.class, BizCustomerMapper.class);
        assertNoMapper(BizContractServiceImpl.class, BizContractMapper.class);
    }

    private long lines(String fileName) throws Exception
    {
        Path source = Paths.get("src", "main", "java", "com", "ruoyi", "system", "service", "impl", fileName);
        return Files.readAllLines(source).size();
    }

    private void assertNoMapper(Class<?> facade, Class<?> mapper)
    {
        Field[] fields = facade.getDeclaredFields();
        assertFalse(Arrays.stream(fields).anyMatch(field -> mapper.isAssignableFrom(field.getType())),
                () -> facade.getSimpleName() + " 不应直接依赖 " + mapper.getSimpleName());
    }
}
