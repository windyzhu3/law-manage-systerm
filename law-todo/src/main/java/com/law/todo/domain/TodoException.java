package com.law.todo.domain;

public class TodoException extends RuntimeException
{
    private final String businessCode;
    public TodoException(String businessCode,String message){super(message);this.businessCode=businessCode;}
    public String getBusinessCode(){return businessCode;}
}
