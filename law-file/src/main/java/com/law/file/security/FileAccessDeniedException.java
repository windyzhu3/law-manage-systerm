package com.law.file.security;

import com.law.file.domain.FileException;

public class FileAccessDeniedException extends FileException
{
    public FileAccessDeniedException(String message){super("FILE_ACCESS_DENIED",message);}
}
