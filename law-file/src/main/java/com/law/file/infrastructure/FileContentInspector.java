package com.law.file.infrastructure;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import com.law.file.domain.FileException;
import com.law.file.security.DetectedContentType;

final class FileContentInspector
{
    private static final int PREFIX_LIMIT=8192;
    private FileContentInspector() { }

    static DetectedContentType inspect(Path path)
    {
        try
        {
            byte[] prefix;
            try(var input=Files.newInputStream(path)){prefix=input.readNBytes(PREFIX_LIMIT);}
            DetectedContentType strong=strongSignature(prefix);
            if(strong!=null)return strong;
            if(zipSignature(prefix))return inspectZip(path);
            return inspectText(path);
        }
        catch(FileException error){throw error;}
        catch(IOException error)
        {throw new FileException("FILE_CONTENT_INSPECTION_FAILED","Unable to inspect staged content",error);}
    }

    private static DetectedContentType strongSignature(byte[] value)
    {
        if(starts(value,'M','Z'))return DetectedContentType.PE_EXECUTABLE;
        if(starts(value,0x7f,'E','L','F'))return DetectedContentType.ELF_EXECUTABLE;
        if(starts(value,'%','P','D','F','-'))return DetectedContentType.PDF;
        if(starts(value,0x89,'P','N','G',13,10,26,10))return DetectedContentType.PNG;
        if(starts(value,0xff,0xd8,0xff))return DetectedContentType.JPEG;
        if(starts(value,'G','I','F','8','7','a')||starts(value,'G','I','F','8','9','a'))
            return DetectedContentType.GIF;
        if(value.length>=12&&starts(value,'R','I','F','F')&&at(value,8,'W','E','B','P'))
            return DetectedContentType.WEBP;
        if(starts(value,0xd0,0xcf,0x11,0xe0,0xa1,0xb1,0x1a,0xe1))
            return DetectedContentType.OLE_COMPOUND;
        return null;
    }

    private static DetectedContentType inspectZip(Path path)
    {
        boolean word=false,excel=false,powerpoint=false;
        try(ZipFile zip=new ZipFile(path.toFile()))
        {
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements())
            {
                String name=entries.nextElement().getName().replace('\\','/').toLowerCase(Locale.ROOT);
                if("word/document.xml".equals(name))word=true;
                else if("xl/workbook.xml".equals(name))excel=true;
                else if("ppt/presentation.xml".equals(name))powerpoint=true;
            }
        }
        catch(ZipException error)
        {throw new FileException("FILE_CONTENT_CONTAINER_INVALID","Invalid file container",error);}
        catch(IOException error)
        {throw new FileException("FILE_CONTENT_CONTAINER_INVALID","Unable to inspect file container",error);}
        int families=(word?1:0)+(excel?1:0)+(powerpoint?1:0);
        if(families!=1)return DetectedContentType.GENERIC_ZIP;
        if(word)return DetectedContentType.OOXML_WORD;
        if(excel)return DetectedContentType.OOXML_EXCEL;
        return DetectedContentType.OOXML_POWERPOINT;
    }

    private static DetectedContentType inspectText(Path path)
    {
        StringBuilder prefix=new StringBuilder(PREFIX_LIMIT);
        try(BufferedReader reader=Files.newBufferedReader(path,StandardCharsets.UTF_8))
        {
            char[] buffer=new char[4096];int count;
            while((count=reader.read(buffer))>=0)
            {
                for(int index=0;index<count;index++)
                {
                    if(buffer[index]==0)return DetectedContentType.UNKNOWN;
                    if(prefix.length()<PREFIX_LIMIT)prefix.append(buffer[index]);
                }
            }
        }
        catch(java.nio.charset.MalformedInputException error){return DetectedContentType.UNKNOWN;}
        catch(IOException error)
        {throw new FileException("FILE_CONTENT_INSPECTION_FAILED","Unable to inspect staged content",error);}
        String normalized=prefix.toString().stripLeading().toLowerCase(Locale.ROOT);
        if(normalized.startsWith("\ufeff"))normalized=normalized.substring(1).stripLeading();
        if(normalized.startsWith("<svg")||(normalized.startsWith("<?xml")&&normalized.contains("<svg")))
            return DetectedContentType.SVG;
        if(normalized.startsWith("<!doctype html")||normalized.startsWith("<html")
            ||normalized.startsWith("<script")||normalized.startsWith("<head")||normalized.startsWith("<body"))
            return DetectedContentType.HTML;
        return DetectedContentType.UTF8_TEXT;
    }

    private static boolean zipSignature(byte[] value)
    {return starts(value,'P','K',3,4)||starts(value,'P','K',5,6)||starts(value,'P','K',7,8);}
    private static boolean starts(byte[] value,int...signature){return at(value,0,signature);}
    private static boolean at(byte[] value,int offset,int...signature)
    {
        if(value.length<offset+signature.length)return false;
        for(int index=0;index<signature.length;index++)
            if((value[offset+index]&0xff)!=(signature[index]&0xff))return false;
        return true;
    }
}
