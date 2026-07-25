-- Publish the governed lead-flow templates without mutating published history.
-- Flyway serializes this migration. Version identities are reserved from the live sequence and
-- then referenced through the temporary code map; no environment-specific identity is assumed.

create temporary table tmp_lead_todo_publish (
  publish_order int not null,
  template_code varchar(64) not null,
  business_type varchar(32) not null,
  event_type varchar(100) not null,
  template_id bigint null,
  source_version_id bigint null,
  version_no int null,
  version_id bigint null,
  definition_json json null,
  primary key (template_code),
  unique key uk_tmp_lead_todo_publish_order (publish_order)
) engine=innodb;

insert into tmp_lead_todo_publish(publish_order,template_code,business_type,event_type) values
(1,'TD-004','LEAD','LEAD_FIRST_CONTACT_VALID'),
(2,'TD-002','LEAD','LEAD_SUSPECT_INVALID_MARKED'),
(3,'TD-003','LEAD','LEAD_RETRY_WINDOW_DUE'),
(4,'TD-001','LEAD','LEAD_ASSIGNED');

update tmp_lead_todo_publish x
join todo_template t on t.template_code=x.template_code
set x.template_id=t.template_id,
    x.source_version_id=(
      select v.version_id
      from todo_template_version v
      where v.template_id=t.template_id
      order by v.version_no desc,v.version_id desc
      limit 1
    ),
    x.version_no=(
      select coalesce(max(v.version_no),0)+1
      from todo_template_version v
      where v.template_id=t.template_id
    );

create temporary table tmp_lead_todo_publish_guard (
  template_count int not null,
  source_count int not null,
  constraint ck_lead_todo_publish_templates check (template_count=4),
  constraint ck_lead_todo_publish_sources check (source_count=4)
) engine=innodb;
insert into tmp_lead_todo_publish_guard(template_count,source_count)
select count(template_id),count(source_version_id) from tmp_lead_todo_publish;
drop temporary table tmp_lead_todo_publish_guard;

set @lead_todo_next_version_id=(
  select greatest(
    coalesce(max(version_id),0)+1,
    coalesce((
      select auto_increment
      from information_schema.tables
      where table_schema=database() and table_name='todo_template_version'
    ),1)
  )
  from todo_template_version
);

update tmp_lead_todo_publish
set version_id=@lead_todo_next_version_id+publish_order-1;

set @td004_version_id=(select version_id from tmp_lead_todo_publish where template_code='TD-004');
set @td002_version_id=(select version_id from tmp_lead_todo_publish where template_code='TD-002');
set @td003_version_id=(select version_id from tmp_lead_todo_publish where template_code='TD-003');
set @td001_version_id=(select version_id from tmp_lead_todo_publish where template_code='TD-001');

-- TD-004 is deliberately a LEAD stage-3 handoff and has no downstream route.
update tmp_lead_todo_publish x
join todo_prd_definition_catalog c on c.template_code=x.template_code
set x.definition_json=json_set(
  c.definition_json,
  '$.event',json_object('eventType','LEAD_FIRST_CONTACT_VALID','payloadVersion',1,'condition',json_object()),
  '$.owner.config',json_object(
    'type','PAYLOAD','field','ownerId',
    'skipUnavailable',false,'useDelegation',false,'requireAvailable',true
  ),
  '$.dod.config',json_object(
    'requiredFields',json_array('progressType','progressAt'),
    'materials',json_array(json_object(
      'type','FOLLOWUP_PROOF','minCount',1,'label','录音、截图、报价或外访凭证'
    )),
    'conditionalRequired',json_array(),
    'validatorRefs',json_array()
  ),
  '$.sla.config',json_object(
    'calendarCode','DEFAULT','minutes',7200,
    'thresholds',json_array(80,100,150),
    'thresholdActions',json_object(
      'SLA_80','REMIND_OWNER',
      'SLA_100','MARK_OVERDUE_SUPERVISOR_VISIBLE',
      'SLA_150','ESCALATE_SUPERVISOR'
    )
  ),
  '$.ui.config',json_object(
    'formCode','TD-004','businessType','LEAD',
    'fields',json_array(
      json_object('key','progressType','type','dict','dictType','law_lead_progress_type','label','实质进展类型'),
      json_object('key','progressAt','type','datetime','label','进展时间'),
      json_object('key','remark','type','textarea','label','进展说明')
    )
  ),
  '$.routing.config',json_object(
    'identityBinding','MIGRATION_DYNAMIC',
    'start','td004',
    'nodes',json_array(
      json_object('key','td004','type','TASK','templateCode','TD-004','templateVersionId',@td004_version_id),
      json_object('key','end','type','END')
    ),
    'edges',json_array(
      json_object('key','td004-end','from','td004','to','end','priority',0)
    )
  ),
  '$.autoActions',json_array(json_object('config',json_object(
    'ruleKey','td004-sla-150','actionType','ESCALATE','capability','ESCALATE',
    'triggerAt','SLA_150','maxAttempts',3,'retryDelayMinutes',5,'claimTimeoutMinutes',15
  ))),
  '$.decisionRefs',json_array()
)
where x.template_code='TD-004';

update tmp_lead_todo_publish x
join todo_prd_definition_catalog c on c.template_code=x.template_code
set x.definition_json=json_set(
  c.definition_json,
  '$.event',json_object('eventType','LEAD_SUSPECT_INVALID_MARKED','payloadVersion',1,'condition',json_object()),
  '$.owner.config',json_object(
    'type','PAYLOAD','field','reviewerId',
    'skipUnavailable',false,'useDelegation',false,'requireAvailable',true
  ),
  '$.dod.config',json_object(
    'requiredFields',json_array('reviewResult','reviewOpinion'),
    'materials',json_array(),
    'conditionalRequired',json_array(),
    'validatorRefs',json_array()
  ),
  '$.sla.config',json_object('calendarCode','DEFAULT','minutes',1440,'onDue','COMPLETE_DEFAULT'),
  '$.ui.config',json_object(
    'formCode','TD-002','businessType','LEAD',
    'fields',json_array(
      json_object('key','reviewResult','type','dict','dictType','law_lead_invalid_review_result','label','复核结果'),
      json_object('key','reviewOpinion','type','textarea','label','复核意见')
    )
  ),
  '$.routing.config',json_object(
    'identityBinding','MIGRATION_DYNAMIC',
    'start','td002',
    'nodes',json_array(
      json_object('key','td002','type','TASK','templateCode','TD-002','templateVersionId',@td002_version_id),
      json_object('key','reviewResult','type','DECISION'),
      json_object('key','reopenedTd001','type','TASK','templateCode','TD-001','templateVersionId',@td001_version_id),
      json_object('key','end','type','END')
    ),
    'edges',json_array(
      json_object('key','td002-result','from','td002','to','reviewResult','priority',0),
      json_object(
        'key','review-invalid','from','reviewResult','to','end','priority',20,
        'condition',json_object('$expression',json_object(
          'version',1,'root',json_object('field','reviewResult','operator','EQ','value','TRUE_INVALID')
        ))
      ),
      json_object(
        'key','review-reopen','from','reviewResult','to','reopenedTd001','priority',10,
        'condition',json_object('$expression',json_object(
          'version',1,'root',json_object('field','reviewResult','operator','EQ','value','MISJUDGED_VALID')
        ))
      ),
      json_object('key','review-default','from','reviewResult','to','end','priority',-1,'default',true),
      json_object('key','reopened-end','from','reopenedTd001','to','end','priority',0)
    )
  ),
  '$.autoActions',json_array(json_object('config',json_object(
    'ruleKey','td002-default-invalid','actionType','COMPLETE_DEFAULT',
    'capability','COMPLETE_DEFAULT','triggerAt','DUE',
    'fields',json_object(
      'reviewResult','TRUE_INVALID',
      'reviewOpinion','超过24小时未复核，系统按规则默认确认无效'
    ),
    'maxAttempts',3,'retryDelayMinutes',5,'claimTimeoutMinutes',15
  ))),
  '$.decisionRefs',json_array()
)
where x.template_code='TD-002';

update tmp_lead_todo_publish x
join todo_prd_definition_catalog c on c.template_code=x.template_code
set x.definition_json=json_set(
  c.definition_json,
  '$.event',json_object('eventType','LEAD_RETRY_WINDOW_DUE','payloadVersion',1,'condition',json_object()),
  '$.owner.config',json_object(
    'type','BUSINESS_OWNER','businessType','LEAD',
    'skipUnavailable',false,'useDelegation',false,'requireAvailable',true
  ),
  '$.dod.config',json_object(
    'requiredFields',json_array('contactResult'),
    'materials',json_array(json_object(
      'type','CONTACT_PROOF','minCount',1,'label','每次拨号记录'
    )),
    'conditionalRequired',json_array(
      json_object('when',json_object('field','contactResult','equals','CONNECTED'),'field','name'),
      json_object('when',json_object('field','contactResult','equals','CONNECTED'),'field','city'),
      json_object('when',json_object('field','contactResult','equals','CONNECTED'),'field','demand'),
      json_object('when',json_object('field','contactResult','equals','CONNECTED'),'field','visited')
    ),
    'validatorRefs',json_array('LeadFirstContactValidator')
  ),
  '$.sla.config',json_object(
    'calendarCode','DEFAULT',
    'schedule',json_object(
      'identityBinding','MIGRATION_DYNAMIC',
      'timezone','Asia/Shanghai',
      'policySource','LEAD_ASSIGNMENT_POLICY',
      'targetTemplateCode','TD-003',
      'targetTemplateVersionId',@td003_version_id,
      'occurrenceKey','planId:windowCode:occurrenceNo',
      'windows',json_array(
        json_object('windowCode','T0','dayOffset',0,'startOffsetMinutes',0,'durationMinutes',120,'maxAttempts',3),
        json_object('windowCode','T1_AM','dayOffset',1,'startTime','09:00:00','endTime','11:00:00','maxAttempts',1),
        json_object('windowCode','T1_NOON','dayOffset',1,'startTime','12:00:00','endTime','14:00:00','maxAttempts',1),
        json_object('windowCode','T1_PM','dayOffset',1,'startTime','15:00:00','endTime','18:00:00','maxAttempts',1),
        json_object('windowCode','T2_AM','dayOffset',2,'startTime','09:00:00','endTime','11:00:00','maxAttempts',1),
        json_object('windowCode','T2_NOON','dayOffset',2,'startTime','12:00:00','endTime','14:00:00','maxAttempts',1),
        json_object('windowCode','T2_PM','dayOffset',2,'startTime','15:00:00','endTime','18:00:00','maxAttempts',1)
      )
    )
  ),
  '$.ui.config',json_object(
    'formCode','TD-003','businessType','LEAD',
    'fields',json_array(
      json_object('key','attemptStage','type','dict','dictType','law_retry_stage','label','重试阶段','readOnly',true),
      json_object('key','attemptCount','type','number','label','拨号次数','readOnly',true),
      json_object('key','contactResult','type','dict','dictType','law_retry_result','label','联系结果'),
      json_object('key','name','type','text','label','姓名','showWhen',json_object('field','contactResult','equals','CONNECTED')),
      json_object('key','city','type','text','label','城市','showWhen',json_object('field','contactResult','equals','CONNECTED')),
      json_object('key','demand','type','textarea','label','诉求','showWhen',json_object('field','contactResult','equals','CONNECTED')),
      json_object('key','visited','type','dict','dictType','law_yes_no_flag','label','是否到所','showWhen',json_object('field','contactResult','equals','CONNECTED'))
    )
  ),
  '$.routing.config',json_object(
    'identityBinding','MIGRATION_DYNAMIC',
    'start','td003',
    'nodes',json_array(
      json_object('key','td003','type','TASK','templateCode','TD-003','templateVersionId',@td003_version_id),
      json_object('key','retryResult','type','DECISION'),
      json_object('key','td004','type','TASK','templateCode','TD-004','templateVersionId',@td004_version_id),
      json_object('key','end','type','END')
    ),
    'edges',json_array(
      json_object('key','td003-result','from','td003','to','retryResult','priority',0),
      json_object(
        'key','retry-connected','from','retryResult','to','td004','priority',30,
        'condition',json_object('$expression',json_object(
          'version',1,'root',json_object('field','result','operator','EQ','value','CONNECTED')
        ))
      ),
      json_object(
        'key','retry-next','from','retryResult','to','end','priority',20,
        'condition',json_object('$expression',json_object(
          'version',1,'root',json_object('field','result','operator','EQ','value','NEXT_WINDOW')
        ))
      ),
      json_object(
        'key','retry-exhausted','from','retryResult','to','end','priority',10,
        'condition',json_object('$expression',json_object(
          'version',1,'root',json_object('field','result','operator','EQ','value','EXHAUSTED')
        ))
      ),
      json_object('key','retry-default','from','retryResult','to','end','priority',-1,'default',true),
      json_object('key','td004-end','from','td004','to','end','priority',0)
    )
  ),
  '$.autoActions',json_array(),
  '$.decisionRefs',json_array()
)
where x.template_code='TD-003';

update tmp_lead_todo_publish x
join todo_prd_definition_catalog c on c.template_code=x.template_code
set x.definition_json=json_set(
  c.definition_json,
  '$.event',json_object('eventType','LEAD_ASSIGNED','payloadVersion',1,'condition',json_object()),
  '$.owner.config',json_object(
    'type','PAYLOAD','field','ownerId',
    'skipUnavailable',false,'useDelegation',false,'requireAvailable',true
  ),
  '$.dod.config',json_object(
    'requiredFields',json_array('contactResult','contactedAt'),
    'materials',json_array(json_object(
      'type','CONTACT_PROOF','minCount',1,'label','通话记录、录音或人工补录凭证'
    )),
    'conditionalRequired',json_array(
      json_object('when',json_object('field','contactResult','equals','VALID'),'field','name'),
      json_object('when',json_object('field','contactResult','equals','VALID'),'field','city'),
      json_object('when',json_object('field','contactResult','equals','VALID'),'field','demand'),
      json_object('when',json_object('field','contactResult','equals','VALID'),'field','visited'),
      json_object('when',json_object('field','contactResult','equals','SUSPECT_INVALID'),'field','invalidReasonCode'),
      json_object('when',json_object('field','contactResult','equals','SUSPECT_INVALID'),'field','salesExplanation')
    ),
    'validatorRefs',json_array('LeadFirstContactValidator')
  ),
  '$.sla.config',json_object(
    'calendarCode','DEFAULT','minutes',30,
    'thresholds',json_array(80,100,150),
    'thresholdActions',json_object(
      'SLA_80','REMIND_OWNER',
      'SLA_100','MARK_OVERDUE_SUPERVISOR_VISIBLE',
      'SLA_150','ESCALATE_SUPERVISOR'
    )
  ),
  '$.ui.config',json_object(
    'formCode','TD-001','businessType','LEAD',
    'fields',json_array(
      json_object('key','contactResult','type','dict','dictType','law_first_contact_result','label','首联结果'),
      json_object('key','contactedAt','type','datetime','label','联系时间'),
      json_object('key','name','type','text','label','姓名','showWhen',json_object('field','contactResult','equals','VALID')),
      json_object('key','city','type','text','label','城市','showWhen',json_object('field','contactResult','equals','VALID')),
      json_object('key','demand','type','textarea','label','诉求','showWhen',json_object('field','contactResult','equals','VALID')),
      json_object('key','visited','type','dict','dictType','law_yes_no_flag','label','是否到所','showWhen',json_object('field','contactResult','equals','VALID')),
      json_object('key','invalidReasonCode','type','dict','dictType','law_lead_invalid_reason','label','无效原因','showWhen',json_object('field','contactResult','equals','SUSPECT_INVALID')),
      json_object('key','salesExplanation','type','textarea','label','销售说明','showWhen',json_object('field','contactResult','equals','SUSPECT_INVALID'))
    )
  ),
  '$.routing.config',json_object(
    'identityBinding','MIGRATION_DYNAMIC',
    'start','td001',
    'nodes',json_array(
      json_object('key','td001','type','TASK','templateCode','TD-001','templateVersionId',@td001_version_id),
      json_object('key','firstResult','type','DECISION'),
      json_object('key','td002','type','TASK','templateCode','TD-002','templateVersionId',@td002_version_id),
      json_object('key','reviewResult','type','DECISION'),
      json_object('key','td004','type','TASK','templateCode','TD-004','templateVersionId',@td004_version_id),
      json_object('key','reopenedTd001','type','TASK','templateCode','TD-001','templateVersionId',@td001_version_id),
      json_object('key','end','type','END')
    ),
    'edges',json_array(
      json_object('key','td001-result','from','td001','to','firstResult','priority',0),
      json_object(
        'key','first-valid','from','firstResult','to','td004','priority',30,
        'condition',json_object('$expression',json_object(
          'version',1,'root',json_object('field','contactResult','operator','EQ','value','VALID')
        ))
      ),
      json_object(
        'key','first-suspect','from','firstResult','to','td002','priority',20,
        'condition',json_object('$expression',json_object(
          'version',1,'root',json_object('field','contactResult','operator','EQ','value','SUSPECT_INVALID')
        ))
      ),
      json_object(
        'key','first-unreachable','from','firstResult','to','end','priority',10,
        'condition',json_object('$expression',json_object(
          'version',1,'root',json_object('field','contactResult','operator','EQ','value','UNREACHABLE')
        ))
      ),
      json_object('key','first-default','from','firstResult','to','end','priority',-1,'default',true),
      json_object('key','td002-result','from','td002','to','reviewResult','priority',0),
      json_object(
        'key','review-invalid','from','reviewResult','to','end','priority',20,
        'condition',json_object('$expression',json_object(
          'version',1,'root',json_object('field','reviewResult','operator','EQ','value','TRUE_INVALID')
        ))
      ),
      json_object(
        'key','review-reopen','from','reviewResult','to','reopenedTd001','priority',10,
        'condition',json_object('$expression',json_object(
          'version',1,'root',json_object('field','reviewResult','operator','EQ','value','MISJUDGED_VALID')
        ))
      ),
      json_object('key','review-default','from','reviewResult','to','end','priority',-1,'default',true),
      json_object('key','reopened-end','from','reopenedTd001','to','end','priority',0),
      json_object('key','td004-end','from','td004','to','end','priority',0)
    )
  ),
  '$.autoActions',json_array(json_object('config',json_object(
    'ruleKey','td001-sla-150','actionType','ESCALATE','capability','ESCALATE',
    'triggerAt','SLA_150','maxAttempts',3,'retryDelayMinutes',5,'claimTimeoutMinutes',15
  ))),
  '$.decisionRefs',json_array()
)
where x.template_code='TD-001';

create temporary table tmp_lead_todo_definition_guard (
  definition_count int not null,
  constraint ck_lead_todo_publish_definitions check (definition_count=4)
) engine=innodb;
insert into tmp_lead_todo_definition_guard(definition_count)
select count(definition_json) from tmp_lead_todo_publish;
drop temporary table tmp_lead_todo_definition_guard;

start transaction;

insert into todo_template_version(
  version_id,template_id,version_no,status,source_version_id,
  owner_rule_json,dod_rule_json,sla_rule_json,next_rule_json,ui_schema_json,
  definition_schema_version,definition_json,compiled_json,definition_hash,
  validation_report_json,published_by,published_time,update_by,update_time
)
select
  x.version_id,x.template_id,x.version_no,'PUBLISHED',x.source_version_id,
  json_extract(x.definition_json,'$.owner.config'),
  json_extract(x.definition_json,'$.dod.config'),
  json_extract(x.definition_json,'$.sla.config'),
  json_extract(x.definition_json,'$.routing.config'),
  json_extract(x.definition_json,'$.ui.config'),
  1,x.definition_json,x.definition_json,
  lower(sha2(cast(x.definition_json as char),256)),
  json_object('errors',json_array(),'warnings',json_array()),
  'migration',sysdate(),'migration',sysdate()
from tmp_lead_todo_publish x
order by x.publish_order;

-- FAILURE_INJECTION_POINT_AFTER_VERSION_INSERT

update todo_template t
join tmp_lead_todo_publish x on x.template_id=t.template_id
set t.current_version=x.version_no,
    t.business_type=x.business_type,
    t.update_by='migration',
    t.update_time=sysdate();

-- Preserve history while making the assignment entry point unique.
update todo_trigger_rule r
join todo_template legacy on legacy.template_id=r.template_id
set r.enabled='N',r.version=r.version+1,r.update_by='migration',r.update_time=sysdate()
where legacy.template_code='LEAD_FIRST_CONTACT' and r.enabled='Y';

update todo_trigger_rule
set enabled='N',version=version+1,update_by='migration',update_time=sysdate()
where event_type='LEAD_ASSIGNED' and enabled='Y';

insert into todo_trigger_rule(
  rule_code,rule_name,event_type,payload_version,template_id,template_version_id,
  business_type,enabled,condition_json,version,sort_order,create_by
)
select
  'TRIGGER_LEAD_ASSIGNED_TD001_V02051','线索分配生成首联待办',
  'LEAD_ASSIGNED',1,x.template_id,x.version_id,'LEAD','Y',null,0,10,'migration'
from tmp_lead_todo_publish x
where x.template_code='TD-001';

-- The catalogue now describes the executable publication, not its superseded blocked draft.
update todo_prd_definition_catalog c
join tmp_lead_todo_publish x on x.template_code=c.template_code
set c.business_type=x.business_type,
    c.definition_package_state='READY',
    c.foundation_state='READY',
    c.production_state='READY',
    c.definition_json=x.definition_json,
    c.routing_plan_json=case x.template_code
      when 'TD-001' then json_object(
        'binding','PUBLISHED_NUMERIC_VERSION','start','TD-001',
        'transitions',json_array(
          json_object('on','VALID','to',json_array('TD-004'),'businessAction','WRITE_LEAD_VALID'),
          json_object('on','SUSPECT_INVALID','to',json_array('TD-002'),'businessAction','MARK_SUSPECT_INVALID'),
          json_object('on','UNREACHABLE','to',json_array('END'),'businessAction','START_RETRY')
        ),
        'notes',json_array('Published TASK nodes use numeric version identities.')
      )
      when 'TD-002' then json_object(
        'binding','PUBLISHED_NUMERIC_VERSION','start','TD-002',
        'transitions',json_array(
          json_object('on','TRUE_INVALID','to',json_array('END'),'businessAction','MOVE_DEAD_POOL'),
          json_object('on','MISJUDGED_VALID','to',json_array('TD-001'),'businessAction','REOPEN_FIRST_CONTACT')
        ),
        'notes',json_array('Controlled DUE completion emits TRUE_INVALID.')
      )
      when 'TD-003' then json_object(
        'binding','PUBLISHED_NUMERIC_VERSION','start','TD-003',
        'transitions',json_array(
          json_object('on','CONNECTED','to',json_array('TD-004'),'businessAction','WRITE_LEAD_VALID'),
          json_object('on','NEXT_WINDOW','to',json_array('END'),'businessAction','SCHEDULE_NEXT_RETRY'),
          json_object('on','EXHAUSTED','to',json_array('END'),'businessAction','TAG_AND_RETURN_POOL')
        ),
        'notes',json_array('Routing uses the server-owned retry result.')
      )
      else json_object(
        'binding','PUBLISHED_NUMERIC_VERSION','start','TD-004',
        'transitions',json_array(
          json_object('on','COMPLETE','to',json_array('END'),'businessAction','RECORD_PROGRESS_HANDOFF')
        ),
        'notes',json_array('Approved stage-3 boundary only.')
      )
    end,
    c.handler_capability_json=json_object(
      'requiredCode',concat(x.template_code,'_COMPLETE'),
      'repositoryStatus','PRESENT',
      'existingCandidate',case when x.template_code='TD-001' then 'LEAD_FIRST_CONTACT' else null end,
      'requireBusinessWriteback',true
    ),
    c.business_dependencies_json=json_array(),
    c.blockers_json=json_array(),
    c.update_time=sysdate();

insert into sys_dict_type(dict_name,dict_type,status,create_by,create_time,remark)
select '线索实质进展类型','law_lead_progress_type','0','migration',sysdate(),'TD-004 dynamic form'
where not exists(
  select 1 from sys_dict_type where dict_type='law_lead_progress_type'
);

insert into sys_dict_data(
  dict_sort,dict_label,dict_value,dict_type,css_class,list_class,is_default,status,
  create_by,create_time,remark
)
select x.dict_sort,x.dict_label,x.dict_value,'law_lead_progress_type','',x.list_class,
  'N','0','migration',sysdate(),x.dict_label
from (
  select 1 dict_sort,'电话沟通' dict_label,'PHONE' dict_value,'primary' list_class union all
  select 2,'微信沟通','WECHAT','success' union all
  select 3,'到所面谈','MEETING','warning' union all
  select 4,'外访','VISIT','warning' union all
  select 5,'报价/方案','QUOTE','primary' union all
  select 6,'其他','OTHER','info'
) x
where not exists(
  select 1 from sys_dict_data d
  where d.dict_type='law_lead_progress_type' and d.dict_value=x.dict_value
);

commit;

drop temporary table tmp_lead_todo_publish;
