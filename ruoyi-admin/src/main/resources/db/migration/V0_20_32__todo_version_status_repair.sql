-- Forward-only repair: immutable release rows use PUBLISHED/RETIRED; DRAFT remains valid for editors.
insert into sys_dict_data(dict_sort,dict_label,dict_value,dict_type,css_class,list_class,is_default,status,create_by,create_time,remark)
select 3,'Retired','RETIRED','law_todo_version_status','','warning','N','0','admin',sysdate(),'Immutable retired release version'
where not exists (
  select 1 from sys_dict_data
  where dict_type='law_todo_version_status' and dict_value='RETIRED'
);

update sys_dict_data
set dict_label='Retired',list_class='warning',status='0',update_by='admin',update_time=sysdate(),remark='Immutable retired release version'
where dict_type='law_todo_version_status' and dict_value='RETIRED';

update sys_dict_data
set status='1',update_by='admin',update_time=sysdate(),remark='Disabled: runtime release status is RETIRED'
where dict_type='law_todo_version_status' and dict_value='ROLLED_BACK';
