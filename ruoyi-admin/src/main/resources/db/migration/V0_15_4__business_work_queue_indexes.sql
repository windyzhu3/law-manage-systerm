-- Composite indexes for the most frequent owner queues and matter dashboards.
-- Keep these separate from the original module scripts so deployed databases
-- receive the same performance baseline through Flyway.

alter table biz_case
    add key idx_case_work_queue (
        del_flag, case_status, next_key_date, update_time, case_id
    ),
    add key idx_case_owner_queue (
        owner_id, del_flag, case_status, update_time, case_id
    ),
    add key idx_case_lawyer_queue (
        main_lawyer_id, del_flag, case_status, update_time, case_id
    );

alter table biz_contract
    add key idx_contract_owner_queue (
        owner_id, del_flag, contract_status, update_time, contract_id
    ),
    add key idx_contract_status_queue (
        del_flag, contract_status, update_time, contract_id
    );

alter table biz_lead
    add key idx_lead_owner_queue (
        owner_id, pool_status, del_flag, update_time, lead_id
    ),
    add key idx_lead_pool_queue (
        pool_status, del_flag, update_time, lead_id
    );
