-- Repair the shared yes/no labels used by published Lead Todo dynamic forms.
-- Stable dictionary identity is authoritative; workflow and security bindings are untouched.
update sys_dict_data
set dict_label=case dict_value
  when '1' then '是'
  when '0' then '否'
  else dict_label
end,
update_by='migration',
update_time=current_timestamp
where dict_type='law_yes_no_flag'
  and dict_value in ('0','1');
