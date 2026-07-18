package com.law.file.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import com.law.file.domain.FileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FileContentPolicyTest
{
    private final FileContentPolicy policy=new FileContentPolicy();

    @ParameterizedTest
    @MethodSource("allowedFiles")
    void exact_allow_matrix_returns_canonical_mime(String name,String declared,
        DetectedContentType detected,String canonical)
    {
        policy.validateRegistration(name,declared);

        assertEquals(canonical,policy.requireMatchingContent(name,declared,name,declared,detected));
    }

    @ParameterizedTest
    @MethodSource("blockedRegistrations")
    void active_executable_container_and_generic_types_are_blocked(String name,String declared)
    {
        assertCode("FILE_CONTENT_TYPE_BLOCKED",()->policy.validateRegistration(name,declared));
    }

    @Test void unsupported_extension_is_rejected()
    {
        assertCode("FILE_CONTENT_TYPE_UNSUPPORTED",
            ()->policy.validateRegistration("archive.zip","application/zip"));
    }

    @Test void declared_mime_must_match_extension()
    {
        assertCode("FILE_CONTENT_TYPE_MISMATCH",
            ()->policy.validateRegistration("proof.pdf","image/png"));
    }

    @Test void dangerous_earlier_extension_is_blocked()
    {
        assertCode("FILE_CONTENT_TYPE_BLOCKED",
            ()->policy.validateRegistration("invoice.exe.pdf","application/pdf"));
    }

    @Test void multipart_name_and_mime_must_match_registered_metadata()
    {
        assertCode("FILE_CONTENT_TYPE_MISMATCH",()->policy.requireMatchingContent(
            "proof.pdf","application/pdf","other.pdf","application/pdf",DetectedContentType.PDF));
        assertCode("FILE_CONTENT_TYPE_MISMATCH",()->policy.requireMatchingContent(
            "proof.pdf","application/pdf","proof.pdf","text/plain",DetectedContentType.PDF));
    }

    @Test void detected_active_or_executable_content_is_blocked_before_type_comparison()
    {
        assertCode("FILE_CONTENT_TYPE_BLOCKED",()->policy.requireMatchingContent(
            "proof.pdf","application/pdf","proof.pdf","application/pdf",DetectedContentType.HTML));
        assertCode("FILE_CONTENT_TYPE_BLOCKED",()->policy.requireMatchingContent(
            "notes.txt","text/plain","notes.txt","text/plain",DetectedContentType.PE_EXECUTABLE));
    }

    @Test void detected_content_and_office_family_must_match_extension()
    {
        assertCode("FILE_CONTENT_TYPE_MISMATCH",()->policy.requireMatchingContent(
            "proof.pdf","application/pdf","proof.pdf","application/pdf",DetectedContentType.PNG));
        assertCode("FILE_CONTENT_TYPE_MISMATCH",()->policy.requireMatchingContent(
            "brief.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "brief.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            DetectedContentType.OOXML_WORD));
    }

    @Test void generic_zip_and_unknown_content_are_unsupported()
    {
        assertCode("FILE_CONTENT_TYPE_UNSUPPORTED",()->policy.requireMatchingContent(
            "brief.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "brief.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            DetectedContentType.GENERIC_ZIP));
        assertCode("FILE_CONTENT_TYPE_UNSUPPORTED",()->policy.requireMatchingContent(
            "notes.txt","text/plain","notes.txt","text/plain",DetectedContentType.UNKNOWN));
    }

    private static Stream<Arguments> allowedFiles()
    {
        return Stream.of(
            Arguments.of("proof.pdf","application/pdf",DetectedContentType.PDF,"application/pdf"),
            Arguments.of("image.png","image/png",DetectedContentType.PNG,"image/png"),
            Arguments.of("photo.jpg","image/jpg",DetectedContentType.JPEG,"image/jpeg"),
            Arguments.of("photo.jpeg","image/jpeg",DetectedContentType.JPEG,"image/jpeg"),
            Arguments.of("scan.gif","image/gif",DetectedContentType.GIF,"image/gif"),
            Arguments.of("scan.webp","image/webp",DetectedContentType.WEBP,"image/webp"),
            Arguments.of("notes.txt","text/plain; charset=UTF-8",DetectedContentType.UTF8_TEXT,"text/plain"),
            Arguments.of("audit.log","text/plain",DetectedContentType.UTF8_TEXT,"text/plain"),
            Arguments.of("items.csv","text/csv",DetectedContentType.UTF8_TEXT,"text/csv"),
            Arguments.of("items.csv","text/plain",DetectedContentType.UTF8_TEXT,"text/plain"),
            Arguments.of("brief.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                DetectedContentType.OOXML_WORD,"application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Arguments.of("ledger.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                DetectedContentType.OOXML_EXCEL,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Arguments.of("slides.pptx","application/vnd.openxmlformats-officedocument.presentationml.presentation",
                DetectedContentType.OOXML_POWERPOINT,"application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Arguments.of("brief.doc","application/msword",DetectedContentType.OLE_COMPOUND,"application/msword"),
            Arguments.of("ledger.xls","application/vnd.ms-excel",DetectedContentType.OLE_COMPOUND,"application/vnd.ms-excel"),
            Arguments.of("slides.ppt","application/vnd.ms-powerpoint",DetectedContentType.OLE_COMPOUND,"application/vnd.ms-powerpoint")
        );
    }

    private static Stream<Arguments> blockedRegistrations()
    {
        return Stream.of(
            Arguments.of("active.html","text/html"),Arguments.of("active.svg","image/svg+xml"),
            Arguments.of("program.exe","application/x-msdownload"),Arguments.of("library.dll","application/octet-stream"),
            Arguments.of("script.js","application/javascript"),Arguments.of("script.ps1","text/plain"),
            Arguments.of("payload.sh","text/plain"),Arguments.of("archive.jar","application/java-archive"),
            Arguments.of("unknown.bin","application/octet-stream")
        );
    }

    private static void assertCode(String expected,org.junit.jupiter.api.function.Executable action)
    {
        FileException error=assertThrows(FileException.class,action);
        assertEquals(expected,error.getBusinessCode());
    }
}
