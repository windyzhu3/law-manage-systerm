package com.law.file.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.law.file.domain.FileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageAdapterTest
{
    @TempDir Path root;

    @Test void verifies_size_and_sha256_before_publishing_object() throws Exception
    {
        LocalFileStorageAdapter adapter=new LocalFileStorageAdapter(root);
        assertThrows(FileException.class,()->adapter.store(new ByteArrayInputStream("abc".getBytes()),"objects/a",4L,
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
        assertFalse(Files.exists(root.resolve("objects/a")));
    }

    @Test void rejects_traversal_for_store_and_read()
    {
        LocalFileStorageAdapter adapter=new LocalFileStorageAdapter(root);
        assertThrows(FileException.class,()->adapter.store(new ByteArrayInputStream(new byte[0]),"../outside",0L,
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
        assertThrows(FileException.class,()->adapter.read("C:/Windows/win.ini"));
    }
}
