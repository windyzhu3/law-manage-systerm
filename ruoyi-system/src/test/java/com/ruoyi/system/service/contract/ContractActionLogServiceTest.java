package com.ruoyi.system.service.contract;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.BizContractMapper;

@ExtendWith(MockitoExtension.class)
class ContractActionLogServiceTest
{
    @Mock private BizContractMapper mapper;

    @Test
    void recordReturnsGeneratedLogId()
    {
        when(mapper.insertStatusLog(any())).thenAnswer(invocation -> {
            invocation.<ContractStatusLogRecord>getArgument(0).setLogId(91L);
            return 1;
        });

        Long id = service().record(10L, "0", "1", "submit", "提交审批", actor());

        assertEquals(91L, id);
    }

    @Test
    void missingGeneratedIdIsReportedAsConcurrentModification()
    {
        when(mapper.insertStatusLog(any())).thenReturn(1);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service().record(10L, null, "1", "submit", "提交审批", actor()));

        assertEquals("CONCURRENT_MODIFICATION", exception.getBusinessCode());
    }

    @Test
    void mapperXmlReturnsTheGeneratedLogId() throws Exception
    {
        try (var stream = getClass().getResourceAsStream("/mapper/system/BizContractMapper.xml"))
        {
            assertNotNull(stream);
            String xml = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(xml.contains("useGeneratedKeys=\"true\""));
            assertTrue(xml.contains("keyProperty=\"logId\""));
            assertTrue(xml.contains("keyColumn=\"log_id\""));
        }
    }

    private ContractActionLogService service()
    {
        return new ContractActionLogService(mapper);
    }
}
