package com.ruoyi.system.foundation;

public class FoundationTestIdentityException extends RuntimeException
{
    private final FoundationTestIdentityErrorCode code;

    public FoundationTestIdentityException(FoundationTestIdentityErrorCode code, String objectKey)
    {
        super(code + (objectKey == null || objectKey.isBlank() ? "" : " [" + objectKey + "]"));
        this.code = code;
    }

    public FoundationTestIdentityErrorCode getCode()
    {
        return code;
    }
}
