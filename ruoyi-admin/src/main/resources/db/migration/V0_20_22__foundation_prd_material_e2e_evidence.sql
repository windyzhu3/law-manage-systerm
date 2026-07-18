update todo_foundation_file_security_requirement
set source_status='CONFIRMED',
    source_ref='ruoyi-admin/src/test/java/com/ruoyi/web/migration/FileMaterialEndToEndTest.java',
    remark='真实MySQL、MyBatis仓储和本地对象存储覆盖全部PRD DoD材料类型的上传、预览、下载、单次令牌与访问审计'
where gate_code='G-05'
  and requirement_code='PRD_MATERIAL_TYPE_E2E'
  and source_status='NEEDS_EVIDENCE';
