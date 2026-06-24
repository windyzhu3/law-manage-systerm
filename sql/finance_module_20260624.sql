-- 财务中心初始化脚本：菜单、按钮权限、财务字典与财务角色

insert into sys_dict_type(dict_name, dict_type, status, create_by, create_time, remark)
select item.dict_name, item.dict_type, '0', 'admin', sysdate(), item.dict_name
from (
  select '财务应收状态' dict_name, 'law_finance_receivable_status' dict_type union all
  select '财务账龄区间', 'law_finance_age_bucket' union all
  select '财务付款方式', 'law_finance_payment_method' union all
  select '财务发票类型', 'law_finance_invoice_type'
) item
where not exists(select 1 from sys_dict_type where dict_type=item.dict_type);

insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select item.sort, item.label, item.value, item.type, '', item.class, item.def, '0', 'admin', sysdate(), item.label
from (
  select 1 sort, '待回款' label, 'pending' value, 'law_finance_receivable_status' type, 'warning' class, 'Y' def union all
  select 2, '部分回款', 'partial', 'law_finance_receivable_status', 'primary', 'N' union all
  select 3, '已回款', 'received', 'law_finance_receivable_status', 'success', 'N' union all
  select 4, '已逾期', 'overdue', 'law_finance_receivable_status', 'danger', 'N' union all
  select 5, '已驳回', 'rejected', 'law_finance_receivable_status', 'info', 'N' union all
  select 1, '未逾期', 'current', 'law_finance_age_bucket', 'success', 'Y' union all
  select 2, '1-30天', 'd30', 'law_finance_age_bucket', 'primary', 'N' union all
  select 3, '31-60天', 'd60', 'law_finance_age_bucket', 'warning', 'N' union all
  select 4, '61-90天', 'd90', 'law_finance_age_bucket', 'danger', 'N' union all
  select 5, '90天以上', 'd90plus', 'law_finance_age_bucket', 'danger', 'N' union all
  select 1, '银行转账', 'bank', 'law_finance_payment_method', 'primary', 'Y' union all
  select 2, '微信', 'wechat', 'law_finance_payment_method', 'success', 'N' union all
  select 3, '支付宝', 'alipay', 'law_finance_payment_method', 'primary', 'N' union all
  select 4, '现金', 'cash', 'law_finance_payment_method', 'warning', 'N' union all
  select 5, '其他', 'other', 'law_finance_payment_method', 'info', 'N' union all
  select 1, '增值税专用发票', 'special', 'law_finance_invoice_type', 'primary', 'Y' union all
  select 2, '增值税普通发票', 'normal', 'law_finance_invoice_type', 'success', 'N' union all
  select 3, '电子发票', 'electronic', 'law_finance_invoice_type', 'info', 'N'
) item
where not exists(select 1 from sys_dict_data where dict_type=item.type and dict_value=item.value);

insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
select '财务中心',0,9,'finance',null,'','Finance',1,0,'M','0','0','','money','admin',sysdate(),'财务中心'
where not exists(select 1 from sys_menu where parent_id=0 and path='finance');

set @finance_menu_id = (select menu_id from sys_menu where parent_id=0 and path='finance' limit 1);

update sys_menu
set menu_name='财务中心',
    order_num=9,
    path='finance',
    component=null,
    query='',
    route_name='Finance',
    is_frame=1,
    is_cache=0,
    menu_type='M',
    visible='0',
    status='0',
    icon='money',
    update_by='admin',
    update_time=sysdate()
where menu_id=@finance_menu_id;

insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
select item.menu_name,@finance_menu_id,item.order_num,item.path,'finance/index',item.query,item.route_name,1,0,'C','0','0',item.perms,item.icon,'admin',sysdate(),item.menu_name
from (
  select '财务总览' menu_name,1 order_num,'overview' path,'{"module":"overview"}' query,'FinanceOverview' route_name,'finance:overview' perms,'dashboard' icon union all
  select '应收管理',2,'receivable','{"module":"receivable"}','FinanceReceivable','finance:receivable:list','money' union all
  select '回款确认',3,'payment','{"module":"payment"}','FinancePayment','finance:payment:list','wallet' union all
  select '发票管理',4,'invoice','{"module":"invoice"}','FinanceInvoice','finance:invoice:list','documentation' union all
  select '费用报销',5,'expense','{"module":"expense"}','FinanceExpense','finance:expense:list','money' union all
  select '财务报表',6,'report','{"module":"report"}','FinanceReport','finance:report:list','chart' 
) item
where @finance_menu_id is not null
  and not exists(select 1 from sys_menu where parent_id=@finance_menu_id and path=item.path);

update sys_menu m
join (
  select '财务总览' menu_name,1 order_num,'overview' path,'{"module":"overview"}' query,'FinanceOverview' route_name,'finance:overview' perms,'dashboard' icon union all
  select '应收管理',2,'receivable','{"module":"receivable"}','FinanceReceivable','finance:receivable:list','money' union all
  select '回款确认',3,'payment','{"module":"payment"}','FinancePayment','finance:payment:list','wallet' union all
  select '发票管理',4,'invoice','{"module":"invoice"}','FinanceInvoice','finance:invoice:list','documentation' union all
  select '费用报销',5,'expense','{"module":"expense"}','FinanceExpense','finance:expense:list','money' union all
  select '财务报表',6,'report','{"module":"report"}','FinanceReport','finance:report:list','chart'
) item on item.path=m.path
set m.menu_name=item.menu_name,
    m.parent_id=@finance_menu_id,
    m.order_num=item.order_num,
    m.component='finance/index',
    m.query=item.query,
    m.route_name=item.route_name,
    m.perms=item.perms,
    m.icon=item.icon,
    m.menu_type='C',
    m.visible='0',
    m.status='0',
    m.update_by='admin',
    m.update_time=sysdate()
where @finance_menu_id is not null
  and m.parent_id=@finance_menu_id;

insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
select item.menu_name, parent.menu_id, item.order_num, '#', '', '', '', 1, 0, 'F', '0', '0', item.perms, '#', 'admin', sysdate(), item.menu_name
from (
  select '应收查询' menu_name,'finance:receivable:list' parent_perm,1 order_num,'finance:receivable:query' perms union all
  select '回款查询','finance:payment:list',1,'finance:payment:query' union all
  select '确认回款','finance:payment:list',2,'finance:payment:confirm' union all
  select '驳回回款','finance:payment:list',3,'finance:payment:reject' union all
  select '发票查询','finance:invoice:list',1,'finance:invoice:query' union all
  select '开票处理','finance:invoice:list',2,'finance:invoice:handle' union all
  select '费用查询','finance:expense:list',1,'finance:expense:query' union all
  select '费用处理','finance:expense:list',2,'finance:expense:edit' union all
  select '报表查询','finance:report:list',1,'finance:report:query'
) item
join sys_menu parent on parent.perms=item.parent_perm and parent.parent_id=@finance_menu_id
where not exists(select 1 from sys_menu where perms=item.perms);

update sys_menu btn
join (
  select '应收查询' menu_name,'finance:receivable:list' parent_perm,1 order_num,'finance:receivable:query' perms union all
  select '回款查询','finance:payment:list',1,'finance:payment:query' union all
  select '确认回款','finance:payment:list',2,'finance:payment:confirm' union all
  select '驳回回款','finance:payment:list',3,'finance:payment:reject' union all
  select '发票查询','finance:invoice:list',1,'finance:invoice:query' union all
  select '开票处理','finance:invoice:list',2,'finance:invoice:handle' union all
  select '费用查询','finance:expense:list',1,'finance:expense:query' union all
  select '费用处理','finance:expense:list',2,'finance:expense:edit' union all
  select '报表查询','finance:report:list',1,'finance:report:query'
) item on item.perms=btn.perms
join sys_menu parent on parent.perms=item.parent_perm and parent.parent_id=@finance_menu_id
set btn.menu_name=item.menu_name,
    btn.parent_id=parent.menu_id,
    btn.order_num=item.order_num,
    btn.path='#',
    btn.component='',
    btn.query='',
    btn.route_name='',
    btn.menu_type='F',
    btn.visible='0',
    btn.status='0',
    btn.update_by='admin',
    btn.update_time=sysdate()
where @finance_menu_id is not null;

insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time,remark)
select '财务人员','finance_manager',34,'4',1,1,'0','0','admin',sysdate(),'财务中心应收、回款、开票、费用与报表管理'
where not exists(select 1 from sys_role where role_key='finance_manager' and del_flag='0');

set @finance_role_id=(select role_id from sys_role where role_key='finance_manager' and del_flag='0' limit 1);
set @case_manager_role_id=(select role_id from sys_role where role_key='case_manager' and del_flag='0' limit 1);
set @law_partner_manager_role_id=(select role_id from sys_role where role_key='law_partner_manager' and del_flag='0' limit 1);

insert into sys_role_menu(role_id,menu_id)
select @finance_role_id,m.menu_id
from sys_menu m
where @finance_role_id is not null
  and (m.menu_id=@finance_menu_id or m.parent_id=@finance_menu_id or m.perms like 'finance:%')
  and not exists(select 1 from sys_role_menu rm where rm.role_id=@finance_role_id and rm.menu_id=m.menu_id);

insert into sys_role_menu(role_id,menu_id)
select @case_manager_role_id,m.menu_id
from sys_menu m
where @case_manager_role_id is not null
  and (m.menu_id=@finance_menu_id or m.parent_id=@finance_menu_id or m.perms in ('finance:overview','finance:receivable:list','finance:receivable:query','finance:expense:list','finance:expense:query','finance:report:list','finance:report:query'))
  and not exists(select 1 from sys_role_menu rm where rm.role_id=@case_manager_role_id and rm.menu_id=m.menu_id);

insert into sys_role_menu(role_id,menu_id)
select @law_partner_manager_role_id,m.menu_id
from sys_menu m
where @law_partner_manager_role_id is not null
  and (m.menu_id=@finance_menu_id or m.parent_id=@finance_menu_id or m.perms like 'finance:%')
  and not exists(select 1 from sys_role_menu rm where rm.role_id=@law_partner_manager_role_id and rm.menu_id=m.menu_id);
