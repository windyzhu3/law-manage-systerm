package com.law.file.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.law.file.domain.FileException;
import com.law.file.security.DetectedContentType;
import com.law.file.spi.FileStoragePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageAdapterTest
{
    @TempDir Path root;

    @Test void verifies_size_and_sha256_before_publishing_object() throws Exception
    {
        LocalFileStorageAdapter adapter=new LocalFileStorageAdapter(root);
        assertThrows(FileException.class,()->adapter.stage(new ByteArrayInputStream("abc".getBytes()),4L,
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
        assertFalse(Files.exists(root.resolve("objects/a")));
    }

    @Test void rejects_traversal_for_store_and_read()
    {
        LocalFileStorageAdapter adapter=new LocalFileStorageAdapter(root);
        assertThrows(FileException.class,()->adapter.publish(
            new FileStoragePort.StagedObject("staged/missing",0L,"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
            "../outside"));
        assertThrows(FileException.class,()->adapter.read("C:/Windows/win.ini"));
    }

    @Test void staged_content_can_be_published_or_aborted() throws Exception
    {
        LocalFileStorageAdapter adapter=new LocalFileStorageAdapter(root);
        String hash="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        var staged=adapter.stage(new ByteArrayInputStream("abc".getBytes()),3L,hash);
        var stored=adapter.publish(staged,"objects/a");
        assertEquals(hash,stored.sha256());
        assertArrayEquals("abc".getBytes(),adapter.read("objects/a").readAllBytes());

        var abandoned=adapter.stage(new ByteArrayInputStream("abc".getBytes()),3L,hash);
        adapter.abort(abandoned);
        assertThrows(FileException.class,()->adapter.publish(abandoned,"objects/b"));
    }

    @Test void staged_content_is_inspected_only_inside_the_staging_area() throws Exception
    {
        LocalFileStorageAdapter adapter=new LocalFileStorageAdapter(root);
        byte[] content="%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String hash=java.util.HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(content));
        var staged=adapter.stage(new ByteArrayInputStream(content),content.length,hash);

        assertEquals(DetectedContentType.PDF,adapter.inspect(staged));
        assertThrows(FileException.class,()->adapter.inspect(
            new FileStoragePort.StagedObject("objects/not-staged",content.length,hash)));
    }
}
