package com.ruoyi.system.service.contract;

import java.util.List;
import com.ruoyi.system.domain.BizContract;

public record ContractImportCommand(List<BizContract> rows, boolean updateSupport, String operator)
{
}
