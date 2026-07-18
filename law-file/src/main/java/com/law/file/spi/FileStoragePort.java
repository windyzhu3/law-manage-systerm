package com.law.file.spi;

import java.io.InputStream;

import com.law.file.security.DetectedContentType;

public interface FileStoragePort
{
    StagedObject stage(InputStream input,long size,String sha256);
    DetectedContentType inspect(StagedObject staged);
    StoredObject publish(StagedObject staged,String objectKey);
    void abort(StagedObject staged);
    void delete(String objectKey);
    InputStream read(String objectKey);
    record StagedObject(String stagingKey,long size,String sha256) { }
    record StoredObject(String objectKey,long size,String sha256) { }
}
