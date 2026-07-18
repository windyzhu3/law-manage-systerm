package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class FileSecurityReviewPackageContractTest
{
    @Test void review_package_is_complete_but_never_self_approves() throws Exception
    {
        String document=Files.readString(Path.of("..","doc","reviews",
            "v0.2-foundation-g05-file-security-review-package.md"));

        assertTrue(document.contains("PENDING_INDEPENDENT_REVIEW"));
        for(String code:List.of("OBJECT_VERSION_RELATION_MODEL","OBJECT_RELATION_ACCESS_POLICY",
            "SINGLE_USE_RELATION_TOKEN","ACCESS_AUDIT_TRAIL","STORAGE_CLEANUP_COMPENSATION",
            "PRD_MATERIAL_TYPE_E2E","SECURITY_REVIEW_SIGNOFF"))
            assertTrue(document.contains(code),"Missing G-05 requirement "+code);
        assertTrue(document.contains("FileMaterialEndToEndTest.java"));
        assertTrue(document.contains("21"));
        assertTrue(document.contains("MIME/内容识别"));
        assertTrue(document.contains("恶意文件扫描"));
        assertTrue(document.contains("上传大小/容量配额"));
        assertTrue(document.contains("Reviewer：未指定"));
        assertTrue(document.contains("签字/审批记录：未提供"));
        assertTrue(document.contains("不得由开发人员代签"));
        assertFalse(document.contains("APPROVED_BY_DEVELOPMENT"));
    }

    @Test void forward_migration_links_the_package_without_approving_or_granting_anything() throws Exception
    {
        String sql=Files.readString(Path.of("..","ruoyi-admin","src","main","resources","db","migration",
            "V0_20_23__foundation_file_security_review_package.sql")).toLowerCase().replaceAll("\\s+"," ");

        assertTrue(sql.contains("requirement_code='security_review_signoff'"));
        assertTrue(sql.contains("source_status='needs_review'"));
        assertTrue(sql.contains("v0.2-foundation-g05-file-security-review-package.md"));
        assertFalse(sql.contains("todo_admission_evidence"));
        assertFalse(sql.contains("sys_role"));
        assertFalse(sql.contains("sys_role_menu"));
        assertFalse(sql.contains("reviewer_user_id"));
        assertFalse(sql.contains("owner_user_id"));
        assertFalse(sql.contains("'approved'"));
    }
}
