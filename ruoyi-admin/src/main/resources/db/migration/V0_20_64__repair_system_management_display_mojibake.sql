-- Repair legacy UTF-8 text that was decoded as latin1 before being stored.
-- The guard only changes values whose latin1 reversal produces Chinese text,
-- so account names, role keys and intentional English labels remain unchanged.

update sys_user
set nick_name=convert(cast(convert(nick_name using latin1) as binary) using utf8mb4)
where nick_name<>''
  and nick_name not regexp '[一-鿿]'
  and convert(cast(convert(nick_name using latin1) as binary) using utf8mb4) regexp '[一-鿿]';

update sys_user
set remark=convert(cast(convert(remark using latin1) as binary) using utf8mb4)
where remark is not null and remark<>''
  and remark not regexp '[一-鿿]'
  and convert(cast(convert(remark using latin1) as binary) using utf8mb4) regexp '[一-鿿]';

update sys_role
set role_name=convert(cast(convert(role_name using latin1) as binary) using utf8mb4)
where role_name<>''
  and role_name not regexp '[一-鿿]'
  and convert(cast(convert(role_name using latin1) as binary) using utf8mb4) regexp '[一-鿿]';

update sys_role
set remark=convert(cast(convert(remark using latin1) as binary) using utf8mb4)
where remark is not null and remark<>''
  and remark not regexp '[一-鿿]'
  and convert(cast(convert(remark using latin1) as binary) using utf8mb4) regexp '[一-鿿]';

update sys_dept
set dept_name=convert(cast(convert(dept_name using latin1) as binary) using utf8mb4)
where dept_name<>''
  and dept_name not regexp '[一-鿿]'
  and convert(cast(convert(dept_name using latin1) as binary) using utf8mb4) regexp '[一-鿿]';

update sys_dept
set leader=convert(cast(convert(leader using latin1) as binary) using utf8mb4)
where leader is not null and leader<>''
  and leader not regexp '[一-鿿]'
  and convert(cast(convert(leader using latin1) as binary) using utf8mb4) regexp '[一-鿿]';

update sys_post
set post_name=convert(cast(convert(post_name using latin1) as binary) using utf8mb4)
where post_name<>''
  and post_name not regexp '[一-鿿]'
  and convert(cast(convert(post_name using latin1) as binary) using utf8mb4) regexp '[一-鿿]';

update sys_dict_type
set dict_name=convert(cast(convert(dict_name using latin1) as binary) using utf8mb4)
where dict_name<>''
  and dict_name not regexp '[一-鿿]'
  and convert(cast(convert(dict_name using latin1) as binary) using utf8mb4) regexp '[一-鿿]';

update sys_dict_type
set remark=convert(cast(convert(remark using latin1) as binary) using utf8mb4)
where remark is not null and remark<>''
  and remark not regexp '[一-鿿]'
  and convert(cast(convert(remark using latin1) as binary) using utf8mb4) regexp '[一-鿿]';

update sys_dict_data
set dict_label=convert(cast(convert(dict_label using latin1) as binary) using utf8mb4)
where dict_label<>''
  and dict_label not regexp '[一-鿿]'
  and convert(cast(convert(dict_label using latin1) as binary) using utf8mb4) regexp '[一-鿿]';

update sys_dict_data
set remark=convert(cast(convert(remark using latin1) as binary) using utf8mb4)
where remark is not null and remark<>''
  and remark not regexp '[一-鿿]'
  and convert(cast(convert(remark using latin1) as binary) using utf8mb4) regexp '[一-鿿]';

update sys_config
set config_name=convert(cast(convert(config_name using latin1) as binary) using utf8mb4)
where config_name<>''
  and config_name not regexp '[一-鿿]'
  and convert(cast(convert(config_name using latin1) as binary) using utf8mb4) regexp '[一-鿿]';

update sys_config
set remark=convert(cast(convert(remark using latin1) as binary) using utf8mb4)
where remark is not null and remark<>''
  and remark not regexp '[一-鿿]'
  and convert(cast(convert(remark using latin1) as binary) using utf8mb4) regexp '[一-鿿]';

update sys_job
set job_name=convert(cast(convert(job_name using latin1) as binary) using utf8mb4)
where job_name<>''
  and job_name not regexp '[一-鿿]'
  and convert(cast(convert(job_name using latin1) as binary) using utf8mb4) regexp '[一-鿿]';

update sys_notice
set notice_title=convert(cast(convert(notice_title using latin1) as binary) using utf8mb4)
where notice_title<>''
  and notice_title not regexp '[一-鿿]'
  and convert(cast(convert(notice_title using latin1) as binary) using utf8mb4) regexp '[一-鿿]';

update sys_notice
set remark=convert(cast(convert(remark using latin1) as binary) using utf8mb4)
where remark is not null and remark<>''
  and remark not regexp '[一-鿿]'
  and convert(cast(convert(remark using latin1) as binary) using utf8mb4) regexp '[一-鿿]';

update sys_menu
set remark=convert(cast(convert(remark using latin1) as binary) using utf8mb4)
where remark is not null and remark<>''
  and remark not regexp '[一-鿿]'
  and convert(cast(convert(remark using latin1) as binary) using utf8mb4) regexp '[一-鿿]';
