package com.ruoyi.web.controller.file;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

import com.law.file.application.FileObjectService;
import com.law.file.domain.FileObject.AccessContent;
import com.law.file.domain.FileObject.AccessReceipt;
import com.law.file.domain.FileObject.FileVersion;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.utils.ip.IpUtils;

class FileObjectControllerTest
{
    @Test void retirement_requires_the_dedicated_whole_object_permission() throws Exception
    {
        var method=FileObjectController.class.getDeclaredMethod("retire",Long.class,
            FileObjectController.RetireFileObjectRequest.class);
        assertEquals("@ss.hasPermi('file:object:retire')",method.getAnnotation(PreAuthorize.class).value());
    }

    @Test void completed_upload_response_exposes_file_object_id_but_never_storage_key_or_path() throws Exception
    {
        FileObjectService service=mock(FileObjectService.class);
        when(service.completeUpload(eq("intent-1"),any(),eq("proof.pdf"),eq("application/pdf"),any()))
            .thenReturn(new FileVersion(21L,10L,1,
            "proof.pdf","application/pdf",3L,"hash","initial",7L,Instant.now()));
        try(var security=mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(7L);security.when(SecurityUtils::getUsername).thenReturn("alice");security.when(SecurityUtils::getDeptId).thenReturn(3L);
            AjaxResult response=new FileObjectController(service).complete("intent-1",new MockMultipartFile("file","proof.pdf","application/pdf","abc".getBytes()));
            String json=String.valueOf(response);
            assertTrue(json.contains("10"));assertFalse(json.contains("objects/internal-secret"));assertFalse(json.contains("objectKey"));
            verify(service).completeUpload(eq("intent-1"),any(),eq("proof.pdf"),eq("application/pdf"),any());
        }
    }

    @Test void preview_is_inline_and_records_success_only_after_stream_finishes() throws Exception
    {
        FileObjectService service=mock(FileObjectService.class);
        AccessReceipt receipt=new AccessReceipt("session-1",10L,21L,4L,"PREVIEW",7L,3L,"127.0.0.1");
        when(service.open(eq("token"),any(),eq("127.0.0.1"))).thenReturn(new AccessContent(
            new ByteArrayInputStream("abc".getBytes()),"proof.pdf","application/pdf",3L,"PREVIEW",receipt));

        try(var security=mockStatic(SecurityUtils.class);var ip=mockStatic(IpUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(7L);security.when(SecurityUtils::getUsername).thenReturn("alice");security.when(SecurityUtils::getDeptId).thenReturn(3L);
            ip.when(IpUtils::getIpAddr).thenReturn("127.0.0.1");
            var response=new FileObjectController(service).open("token");
            assertTrue(response.getHeaders().getFirst("Content-Disposition").startsWith("inline"));
            assertEquals("nosniff",response.getHeaders().getFirst("X-Content-Type-Options"));
            assertEquals("sandbox; default-src 'none'",response.getHeaders().getFirst("Content-Security-Policy"));
            assertEquals("no-referrer",response.getHeaders().getFirst("Referrer-Policy"));
            verify(service,never()).completeAccess(any(),anyBoolean(),any());
            response.getBody().writeTo(new ByteArrayOutputStream());
            verify(service).completeAccess(receipt,true,null);
        }
    }

    @Test void active_preview_content_is_forced_to_attachment_and_hardened()
    {
        FileObjectService service=mock(FileObjectService.class);
        AccessReceipt receipt=new AccessReceipt("session-active",10L,21L,4L,"PREVIEW",7L,3L,"127.0.0.1");
        when(service.open(eq("token"),any(),eq("127.0.0.1"))).thenReturn(new AccessContent(
            new ByteArrayInputStream("<script>alert(1)</script>".getBytes()),"proof.html","text/html",25L,"PREVIEW",receipt));

        try(var security=mockStatic(SecurityUtils.class);var ip=mockStatic(IpUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(7L);security.when(SecurityUtils::getUsername).thenReturn("alice");security.when(SecurityUtils::getDeptId).thenReturn(3L);
            ip.when(IpUtils::getIpAddr).thenReturn("127.0.0.1");
            var response=new FileObjectController(service).open("token");
            assertEquals(MediaType.APPLICATION_OCTET_STREAM,response.getHeaders().getContentType());
            assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("attachment"));
            assertEquals("nosniff",response.getHeaders().getFirst("X-Content-Type-Options"));
            assertEquals("sandbox; default-src 'none'",response.getHeaders().getFirst("Content-Security-Policy"));
            assertEquals("no-referrer",response.getHeaders().getFirst("Referrer-Policy"));
        }
    }

    @Test void failed_download_stream_records_failure_and_never_success()
    {
        FileObjectService service=mock(FileObjectService.class);
        AccessReceipt receipt=new AccessReceipt("session-2",10L,21L,4L,"DOWNLOAD",7L,3L,"127.0.0.1");
        InputStream broken=new InputStream(){@Override public int read() throws IOException{throw new IOException("broken");}};
        when(service.open(eq("token"),any(),eq("127.0.0.1"))).thenReturn(new AccessContent(
            broken,"proof.pdf","application/pdf",3L,"DOWNLOAD",receipt));

        try(var security=mockStatic(SecurityUtils.class);var ip=mockStatic(IpUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(7L);security.when(SecurityUtils::getUsername).thenReturn("alice");security.when(SecurityUtils::getDeptId).thenReturn(3L);
            ip.when(IpUtils::getIpAddr).thenReturn("127.0.0.1");
            var response=new FileObjectController(service).open("token");
            assertTrue(response.getHeaders().getFirst("Content-Disposition").startsWith("attachment"));
            assertThrows(IOException.class,()->response.getBody().writeTo(new ByteArrayOutputStream()));
            verify(service).completeAccess(eq(receipt),eq(false),anyString());
            verify(service,never()).completeAccess(receipt,true,null);
        }
    }

    @Test void success_audit_failure_is_not_misreported_as_a_stream_failure()
    {
        FileObjectService service=mock(FileObjectService.class);
        AccessReceipt receipt=new AccessReceipt("session-3",10L,21L,4L,"DOWNLOAD",7L,3L,"127.0.0.1");
        when(service.open(eq("token"),any(),eq("127.0.0.1"))).thenReturn(new AccessContent(
            new ByteArrayInputStream("abc".getBytes()),"proof.pdf","application/pdf",3L,"DOWNLOAD",receipt));
        doThrow(new IllegalStateException("audit unavailable")).when(service).completeAccess(receipt,true,null);

        try(var security=mockStatic(SecurityUtils.class);var ip=mockStatic(IpUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(7L);security.when(SecurityUtils::getUsername).thenReturn("alice");security.when(SecurityUtils::getDeptId).thenReturn(3L);
            ip.when(IpUtils::getIpAddr).thenReturn("127.0.0.1");
            var response=new FileObjectController(service).open("token");
            assertThrows(IllegalStateException.class,()->response.getBody().writeTo(new ByteArrayOutputStream()));
            verify(service,never()).completeAccess(eq(receipt),eq(false),anyString());
        }
    }
}
