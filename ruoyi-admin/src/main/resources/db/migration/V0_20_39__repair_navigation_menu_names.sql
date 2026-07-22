-- Some Windows bootstrap paths loaded the legacy baseline without an explicit
-- UTF-8 client character set and persisted Chinese navigation labels as '?'.
-- Repair only known page/directory rows that are still entirely question marks;
-- correctly localized or user-customized menu names remain untouched.
update sys_menu
set menu_name = case menu_id
  when 1 then '系统管理'
  when 2 then '系统监控'
  when 3 then '系统工具'
  when 4 then '若依官网'
  when 5 then '线索管理'
  when 100 then '用户管理'
  when 101 then '角色管理'
  when 102 then '菜单管理'
  when 103 then '部门管理'
  when 104 then '岗位管理'
  when 105 then '字典管理'
  when 106 then '参数设置'
  when 107 then '通知公告'
  when 108 then '日志管理'
  when 109 then '在线用户'
  when 110 then '定时任务'
  when 111 then '数据监控'
  when 112 then '服务监控'
  when 113 then '缓存监控'
  when 114 then '缓存列表'
  when 115 then '表单构建'
  when 116 then '代码生成'
  when 117 then '系统接口'
  when 200 then '线索工作台'
  when 201 then '全部线索'
  when 202 then '我的线索'
  when 203 then '线索公海'
  when 204 then '跟进任务'
  when 205 then '线索回收站'
  when 206 then '线索设置'
  when 500 then '操作日志'
  when 501 then '登录日志'
  when 2031 then '客户中心'
  when 2032 then '合同中心'
  when 2033 then '客户列表'
  when 2034 then '联系人管理'
  when 2035 then '客户跟进'
  when 2036 then '客户标签'
  when 2037 then '去重合并'
  when 2038 then '合同列表'
  when 2039 then '合同模板'
  when 2040 then '合同审批'
  when 2041 then '收费计划'
  when 2042 then '合同附件'
  when 2043 then '状态记录'
  when 2044 then '编号规则'
  when 2112 then '案管中心'
  when 2113 then '待分案'
  when 2114 then '分案记录'
  when 2115 then '转案审批'
  when 2116 then '律师负载'
  when 2117 then '待确认信息'
  when 2118 then '状态记录'
  when 2135 then '案件中心'
  when 2136 then '案件列表'
  when 2137 then '我的案件'
  when 2138 then '进度记录'
  when 2139 then '关键节点'
  when 2140 then '费用管理'
  when 2141 then '文档资料'
  when 2142 then '结案归档'
  when 2143 then '状态记录'
  when 2182 then '财务中心'
  when 2183 then '财务总览'
  when 2184 then '应收管理'
  when 2185 then '回款确认'
  when 2186 then '发票管理'
  when 2187 then '费用报销'
  when 2188 then '财务报表'
  else menu_name
end,
update_by = 'flyway',
update_time = sysdate()
where menu_id in (
  1,2,3,4,5,100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,
  200,201,202,203,204,205,206,500,501,2031,2032,2033,2034,2035,2036,2037,2038,2039,
  2040,2041,2042,2043,2044,2112,2113,2114,2115,2116,2117,2118,2135,2136,2137,2138,2139,
  2140,2141,2142,2143,2182,2183,2184,2185,2186,2187,2188
)
and menu_type in ('M','C')
and menu_name <> ''
and menu_name not regexp '[^?]';
