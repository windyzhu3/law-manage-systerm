package com.law.file.domain;

/** Non-enumerable upload-intent error used for missing, foreign, expired and otherwise unavailable intents. */
public class FileUploadUnavailableException extends FileException
{
    public FileUploadUnavailableException(){super("FILE_UPLOAD_UNAVAILABLE","Upload intent is unavailable");}
}
