-- Department IDs differ by environment; definition owner rules use this managed stable code instead.
alter table sys_dept add column dept_code varchar(64) null after dept_name;
update sys_dept set dept_code=concat('DEPT_',dept_id) where dept_code is null or dept_code='';
alter table sys_dept modify column dept_code varchar(64) not null;
alter table sys_dept add unique key uk_sys_dept_code (dept_code);
