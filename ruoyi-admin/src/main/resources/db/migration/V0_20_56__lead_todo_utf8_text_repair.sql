-- Repair legacy lead labels that were imported after an extra UTF-8 decode.
-- Stable route, dictionary and setting keys are authoritative; workflow definitions are untouched.
set @lead_root_menu_id=(
  select menu_id
  from sys_menu
  where parent_id=0 and path='lead'
  order by menu_id
  limit 1
);

update sys_menu
set menu_name=case path
  when 'lead' then '线索管理'
  when 'dashboard' then '线索工作台'
  when 'all' then '全部线索'
  when 'mine' then '我的线索'
  when 'pool' then '线索公海'
  when 'followup' then '跟进任务'
  when 'recycle' then '线索回收站'
  when 'settings' then '线索设置'
  else menu_name
end,
update_by='migration',
update_time=current_timestamp
where (menu_id=@lead_root_menu_id or parent_id=@lead_root_menu_id)
  and path in ('lead','dashboard','all','mine','pool','followup','recycle','settings');

update sys_dict_data
set dict_label=case dict_type
  when 'law_lead_status' then case dict_value
    when '0' then '待分配'
    when '1' then '待跟进'
    when '2' then '跟进中'
    when '3' then '已转化'
    when '4' then '无效'
    when '5' then '已关闭'
    else dict_label
  end
  when 'law_lead_priority' then case dict_value
    when '1' then '高优先级'
    when '2' then '中优先级'
    when '3' then '低优先级'
    else dict_label
  end
  when 'law_lead_follow_type' then case dict_value
    when 'phone' then '电话'
    when 'wechat' then '微信'
    when 'meeting' then '面谈'
    when 'email' then '邮件'
    else dict_label
  end
  when 'law_lead_pool_status' then case dict_value
    when '0' then '已有归属'
    when '1' then '公海线索'
    else dict_label
  end
  when 'law_lead_setting_type' then case dict_value
    when 'source' then '线索来源'
    when 'tag' then '线索标签'
    when 'invalid_reason' then '无效原因'
    else dict_label
  end
  else dict_label
end,
update_by='migration',
update_time=current_timestamp
where dict_type in (
  'law_lead_status',
  'law_lead_priority',
  'law_lead_follow_type',
  'law_lead_pool_status',
  'law_lead_setting_type'
);

update biz_lead_setting
set setting_name=case setting_type
  when 'invalid_reason' then case setting_code
    when 'unreachable' then '无法联系'
    when 'duplicate' then '重复线索'
    when 'rejected' then '明确拒绝'
    else setting_name
  end
  when 'source' then case setting_code
    when 'online' then '线上咨询'
    when 'referral' then '客户转介绍'
    when 'activity' then '市场活动'
    when 'walkin' then '到所咨询'
    else setting_name
  end
  when 'tag' then case setting_code
    when 'important' then '重点客户'
    when 'enterprise' then '企业客户'
    else setting_name
  end
  else setting_name
end,
update_by='migration',
update_time=current_timestamp
where (setting_type='invalid_reason' and setting_code in ('unreachable','duplicate','rejected'))
   or (setting_type='source' and setting_code in ('online','referral','activity','walkin'))
   or (setting_type='tag' and setting_code in ('important','enterprise'));
