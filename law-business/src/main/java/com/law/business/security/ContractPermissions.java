package com.law.business.security;

public final class ContractPermissions
{
    public static final String DATA_SCOPE =
        "contract:list,contract:query,contract:add,contract:edit,contract:remove,contract:import,contract:export," +
        "contract:submit,contract:sign,contract:archive,contract:void,contract:terminate," +
        "contract:template:list,contract:template:add,contract:template:edit,contract:template:remove," +
        "contract:approval:list,contract:approval:handle,contract:fee:list,contract:fee:add," +
        "contract:fee:edit,contract:fee:remove,contract:fee:confirm,contract:fee:reject,contract:fee:invoice," +
        "contract:attachment:list,contract:attachment:add,contract:attachment:remove," +
        "contract:status:list,contract:rule:list,contract:rule:edit";

    private ContractPermissions() { }
}
