package com.law.file.security;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.law.file.domain.FileException;
import org.springframework.stereotype.Component;

@Component
public class FileContentPolicy
{
    private record Rule(Set<String> mimes,DetectedContentType detected) { }

    private static final String DOCX="application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PPTX="application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final Set<String> DANGEROUS_EXTENSIONS=Set.of(
        "html","htm","svg","svgz","exe","dll","com","bat","cmd","ps1","js","jar","sh");
    private static final Set<String> BLOCKED_MIMES=Set.of(
        "text/html","image/svg+xml","application/javascript","text/javascript","application/x-javascript",
        "application/x-msdownload","application/x-msdos-program","application/java-archive",
        "application/x-sh","application/x-powershell","application/octet-stream");
    private static final Map<String,Rule> RULES=Map.ofEntries(
        Map.entry("pdf",rule("application/pdf",DetectedContentType.PDF)),
        Map.entry("png",rule("image/png",DetectedContentType.PNG)),
        Map.entry("jpg",new Rule(Set.of("image/jpeg","image/jpg"),DetectedContentType.JPEG)),
        Map.entry("jpeg",new Rule(Set.of("image/jpeg","image/jpg"),DetectedContentType.JPEG)),
        Map.entry("gif",rule("image/gif",DetectedContentType.GIF)),
        Map.entry("webp",rule("image/webp",DetectedContentType.WEBP)),
        Map.entry("txt",rule("text/plain",DetectedContentType.UTF8_TEXT)),
        Map.entry("log",rule("text/plain",DetectedContentType.UTF8_TEXT)),
        Map.entry("csv",new Rule(Set.of("text/csv","text/plain"),DetectedContentType.UTF8_TEXT)),
        Map.entry("docx",rule(DOCX,DetectedContentType.OOXML_WORD)),
        Map.entry("xlsx",rule(XLSX,DetectedContentType.OOXML_EXCEL)),
        Map.entry("pptx",rule(PPTX,DetectedContentType.OOXML_POWERPOINT)),
        Map.entry("doc",rule("application/msword",DetectedContentType.OLE_COMPOUND)),
        Map.entry("xls",rule("application/vnd.ms-excel",DetectedContentType.OLE_COMPOUND)),
        Map.entry("ppt",rule("application/vnd.ms-powerpoint",DetectedContentType.OLE_COMPOUND))
    );

    public void validateRegistration(String fileName,String declaredContentType)
    {
        String extension=extension(fileName);
        String mime=normalizeMime(declaredContentType);
        if(hasDangerousExtension(fileName)||DANGEROUS_EXTENSIONS.contains(extension)||BLOCKED_MIMES.contains(mime))
            blocked();
        Rule rule=RULES.get(extension);
        if(rule==null)unsupported();
        if(!rule.mimes().contains(mime))mismatch();
    }

    public String requireMatchingContent(String registeredFileName,String declaredContentType,
        String transportFileName,String transportContentType,DetectedContentType detected)
    {
        validateRegistration(registeredFileName,declaredContentType);
        String declared=normalizeMime(declaredContentType);
        String transport=normalizeMime(transportContentType);
        if(!registeredFileName.equals(transportFileName)||!declared.equals(transport))mismatch();
        if(detected==null||detected==DetectedContentType.UNKNOWN||detected==DetectedContentType.GENERIC_ZIP)
            unsupported();
        if(List.of(DetectedContentType.HTML,DetectedContentType.SVG,DetectedContentType.PE_EXECUTABLE,
            DetectedContentType.ELF_EXECUTABLE).contains(detected))blocked();
        Rule rule=RULES.get(extension(registeredFileName));
        if(rule.detected()!=detected)mismatch();
        return canonicalMime(declared);
    }

    private static Rule rule(String mime,DetectedContentType detected)
    {return new Rule(Set.of(mime),detected);}

    private static String extension(String fileName)
    {
        if(fileName==null||fileName.isBlank()||fileName.length()>255||fileName.indexOf('/')>=0
            ||fileName.indexOf('\\')>=0||fileName.endsWith(".")||fileName.endsWith(" ")
            ||fileName.chars().anyMatch(value->value<32||value==127))unsupported();
        int dot=fileName.lastIndexOf('.');
        if(dot<=0||dot==fileName.length()-1)unsupported();
        return fileName.substring(dot+1).toLowerCase(Locale.ROOT);
    }

    private static boolean hasDangerousExtension(String fileName)
    {
        String[] parts=fileName.toLowerCase(Locale.ROOT).split("\\.");
        for(int index=1;index<parts.length-1;index++)if(DANGEROUS_EXTENSIONS.contains(parts[index]))return true;
        return false;
    }

    private static String normalizeMime(String value)
    {
        if(value==null||value.isBlank())mismatch();
        String normalized=value.split(";",2)[0].trim().toLowerCase(Locale.ROOT);
        if("image/jpg".equals(normalized))return "image/jpeg";
        return normalized;
    }

    private static String canonicalMime(String normalized)
    {return "image/jpg".equals(normalized)?"image/jpeg":normalized;}

    private static void blocked(){throw new FileException("FILE_CONTENT_TYPE_BLOCKED","File type is blocked");}
    private static void mismatch(){throw new FileException("FILE_CONTENT_TYPE_MISMATCH","File metadata and content type do not match");}
    private static void unsupported(){throw new FileException("FILE_CONTENT_TYPE_UNSUPPORTED","File type is unsupported");}
}
