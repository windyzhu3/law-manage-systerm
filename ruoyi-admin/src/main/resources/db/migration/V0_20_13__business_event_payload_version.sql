alter table business_event add column payload_version int not null default 1 after event_type;
update business_event set payload_version=1 where payload_version is null;
