alter table biz_contract_fee_plan
    add key idx_contract_fee_work_queue (
        confirm_status, invoice_status, plan_receive_date, contract_id, plan_id
    );

alter table business_event
    add key idx_business_event_type_status (event_type, event_status, event_id),
    add key idx_business_event_create_time (create_time, event_id);
