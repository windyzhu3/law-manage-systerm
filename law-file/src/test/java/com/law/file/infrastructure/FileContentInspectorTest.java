package com.law.file.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.law.file.domain.FileException;
import com.law.file.security.DetectedContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileContentInspectorTest
{
    @TempDir Path root;

    @Test void recognizes_strong_binary_signatures() throws Exception
    {
        assertDetected(DetectedContentType.PDF,"%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII));
        assertDetected(DetectedContentType.PNG,new byte[]{(byte)0x89,'P','N','G',13,10,26,10});
        assertDetected(DetectedContentType.JPEG,new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff,(byte)0xe0});
        assertDetected(DetectedContentType.GIF,"GIF89a".getBytes(StandardCharsets.US_ASCII));
        assertDetected(DetectedContentType.WEBP,new byte[]{'R','I','F','F',4,0,0,0,'W','E','B','P'});
        assertDetected(DetectedContentType.OLE_COMPOUND,new byte[]{(byte)0xd0,(byte)0xcf,0x11,(byte)0xe0,
            (byte)0xa1,(byte)0xb1,0x1a,(byte)0xe1});
        assertDetected(DetectedContentType.PE_EXECUTABLE,new byte[]{'M','Z',0,0});
        assertDetected(DetectedContentType.ELF_EXECUTABLE,new byte[]{0x7f,'E','L','F'});
    }

    @Test void recognizes_utf8_text_html_and_svg() throws Exception
    {
        assertDetected(DetectedContentType.UTF8_TEXT,"律所材料\nline2".getBytes(StandardCharsets.UTF_8));
        assertDetected(DetectedContentType.HTML,"  <!doctype html><html>".getBytes(StandardCharsets.UTF_8));
        assertDetected(DetectedContentType.HTML,"<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        assertDetected(DetectedContentType.SVG,
            "<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\">".getBytes(StandardCharsets.UTF_8));
        assertDetected(DetectedContentType.UNKNOWN,new byte[]{(byte)0xc3,0x28});
        assertDetected(DetectedContentType.UNKNOWN,new byte[]{'a',0,'b'});
    }

    @Test void identifies_ooxml_family_from_central_directory() throws Exception
    {
        assertDetected(DetectedContentType.OOXML_WORD,zip("[Content_Types].xml","word/document.xml"));
        assertDetected(DetectedContentType.OOXML_EXCEL,zip("[Content_Types].xml","xl/workbook.xml"));
        assertDetected(DetectedContentType.OOXML_POWERPOINT,zip("[Content_Types].xml","ppt/presentation.xml"));
        assertDetected(DetectedContentType.GENERIC_ZIP,zip("payload.bin"));
        assertDetected(DetectedContentType.GENERIC_ZIP,zip("word/document.xml","xl/workbook.xml"));
    }

    @Test void corrupt_zip_is_rejected_without_exposing_path() throws Exception
    {
        Path path=root.resolve("secret-container.zip");
        Files.write(path,new byte[]{'P','K',3,4,1,2,3});

        FileException error=assertThrows(FileException.class,()->FileContentInspector.inspect(path));

        assertEquals("FILE_CONTENT_CONTAINER_INVALID",error.getBusinessCode());
        org.junit.jupiter.api.Assertions.assertFalse(error.getMessage().contains(path.toString()));
    }

    private void assertDetected(DetectedContentType expected,byte[] content) throws Exception
    {
        Path path=Files.createTempFile(root,"inspect-",".bin");
        Files.write(path,content);
        assertEquals(expected,FileContentInspector.inspect(path));
    }

    private static byte[] zip(String...entries) throws Exception
    {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes))
        {
            for(String name:entries)
            {
                zip.putNextEntry(new ZipEntry(name));
                zip.write('x');
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
