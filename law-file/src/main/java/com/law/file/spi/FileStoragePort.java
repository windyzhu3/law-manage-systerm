package com.law.file.spi;

import java.io.InputStream;

public interface FileStoragePort
{
    StoredObject store(InputStream input,String objectKey,long size,String sha256);
    InputStream read(String objectKey);
    record StoredObject(String objectKey,long size,String sha256) { }
}
