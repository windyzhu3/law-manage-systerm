package com.ruoyi.web.controller.file;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;

import com.law.file.application.FileObjectService;
import com.law.file.domain.FileObject.FileVersion;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileObjectControllerTest
{
    @Test void completed_upload_response_exposes_file_object_id_but_never_storage_key_or_path() throws Exception
    {
        FileObjectService service=mock(FileObjectService.class);
        when(service.completeUpload(eq("intent-1"),any(),any())).thenReturn(new FileVersion(21L,10L,1,
            "objects/internal-secret","proof.pdf","application/pdf",3L,"hash",7L,Instant.now()));
        try(var security=mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(7L);security.when(SecurityUtils::getUsername).thenReturn("alice");security.when(SecurityUtils::getDeptId).thenReturn(3L);
            AjaxResult response=new FileObjectController(service).complete("intent-1",new MockMultipartFile("file","proof.pdf","application/pdf","abc".getBytes()));
            String json=String.valueOf(response);
            assertTrue(json.contains("10"));assertFalse(json.contains("objects/internal-secret"));assertFalse(json.contains("objectKey"));
        }
    }
}
