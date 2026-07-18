-- Materialize only G-02 resources whose stable values are already confirmed by repository evidence.
insert into sys_dict_type(dict_name,dict_type,status,create_by,create_time,remark)
select '律所业务线','law_business_line','0','migration',sysdate(),'G-02仓库已确认的三业务线稳定字典'
where not exists(select 1 from sys_dict_type where dict_type='law_business_line');

insert into sys_dict_data
(dict_sort,dict_label,dict_value,dict_type,css_class,list_class,is_default,status,create_by,create_time,remark)
select item.sort_order,item.label,item.value_code,'law_business_line','',item.list_class,'N','0','migration',sysdate(),
       'G-02仓库已确认的业务线稳定值'
from (
    select 1 sort_order,'非诉' label,'NON_LITIGATION' value_code,'primary' list_class union all
    select 2,'综法','COMPREHENSIVE','success' union all
    select 3,'执行','EXECUTION','warning'
) item
where not exists(
    select 1 from sys_dict_data data
    where data.dict_type='law_business_line' and data.dict_value=item.value_code
);

-- This role key is required by the approved TD Owner fallback catalogue. It is deliberately
-- created without menu, department or user grants; assignment remains an administrator action.
insert into sys_role
(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time,remark)
select '销售人员','sales',35,'5',1,1,'0','0','migration',sysdate(),'G-02确认的Owner稳定角色键；默认无授权'
where not exists(select 1 from sys_role where role_key='sales' and del_flag='0');
