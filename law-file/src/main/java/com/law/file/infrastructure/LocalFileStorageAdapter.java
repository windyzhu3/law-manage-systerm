package com.law.file.infrastructure;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.util.HexFormat;
import java.util.UUID;

import com.law.file.domain.FileException;
import com.law.file.spi.FileStoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorageAdapter implements FileStoragePort
{
    private final Path root;
    public LocalFileStorageAdapter(@Value("${law.file.storage.local.root:${ruoyi.profile}/file-center}") String root){this(Path.of(root));}
    public LocalFileStorageAdapter(Path root){this.root=root.toAbsolutePath().normalize();}

    @Override public StoredObject store(InputStream input,String objectKey,long expectedSize,String expectedSha256)
    {
        validateExpected(expectedSize,expectedSha256);
        Path target=resolve(objectKey);Path temporary=root.resolve(".tmp").resolve(UUID.randomUUID().toString()).normalize();
        try {
            Files.createDirectories(temporary.getParent());Files.createDirectories(target.getParent());
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long size;
            try(DigestInputStream source=new DigestInputStream(input,digest)) { size=Files.copy(source,temporary,StandardCopyOption.REPLACE_EXISTING); }
            String actual=HexFormat.of().formatHex(digest.digest());
            if(size!=expectedSize||!actual.equalsIgnoreCase(expectedSha256)) {
                Files.deleteIfExists(temporary);
                throw new FileException("FILE_CONTENT_MISMATCH","Uploaded content does not match declared size and SHA-256");
            }
            try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(java.nio.file.AtomicMoveNotSupportedException unsupported){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
            return new StoredObject(objectKey,size,actual);
        } catch(FileException error){throw error;}
          catch(Exception error){try{Files.deleteIfExists(temporary);}catch(Exception ignored){}throw new FileException("FILE_STORAGE_WRITE_FAILED","Unable to store file",error);}
    }
    @Override public InputStream read(String objectKey)
    {
        Path target=resolve(objectKey);
        try {
            if(!Files.isRegularFile(target))throw new FileException("FILE_CONTENT_NOT_FOUND","Stored content does not exist");
            return Files.newInputStream(target);
        } catch(FileException error){throw error;}
          catch(Exception error){throw new FileException("FILE_STORAGE_READ_FAILED","Unable to read file",error);}
    }
    private Path resolve(String key)
    {
        if(key==null||key.isBlank()||key.indexOf('\\')>=0||key.startsWith("/")||key.matches("^[A-Za-z]:.*"))
            throw new FileException("FILE_OBJECT_KEY_INVALID","Invalid storage object key");
        Path resolved=root.resolve(key).normalize();
        if(!resolved.startsWith(root)||key.contains(".."))throw new FileException("FILE_OBJECT_KEY_INVALID","Invalid storage object key");
        return resolved;
    }
    private static void validateExpected(long size,String hash)
    {if(size<0||hash==null||!hash.matches("(?i)[0-9a-f]{64}"))throw new FileException("FILE_METADATA_INVALID","Size and SHA-256 are required");}
}
