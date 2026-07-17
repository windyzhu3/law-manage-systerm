package com.law.file.domain;

public class FileException extends RuntimeException
{
    private final String businessCode;
    public FileException(String businessCode,String message){super(message);this.businessCode=businessCode;}
    public FileException(String businessCode,String message,Throwable cause){super(message,cause);this.businessCode=businessCode;}
    public String getBusinessCode(){return businessCode;}
}
