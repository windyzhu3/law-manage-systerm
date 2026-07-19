package com.law.file.application;

import com.law.file.repository.FileObjectRepository;
import com.law.file.infrastructure.FileCleanupAuditAdapter;
import com.law.file.infrastructure.LocalFileStorageAdapter;
import com.law.file.security.FileAccessPolicy;
import com.law.file.security.FileContentPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class FileObjectServiceSpringContextTest
{
    @Test
    void springSelectsProductionConstructorsForEveryMultiConstructorFileBean(@TempDir Path storageRoot)
    {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext())
        {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "law.file.storage.local.root", storageRoot.toString(),
                "ruoyi.profile", storageRoot.toString())));
            context.registerBean(FileObjectRepository.class, () -> mock(FileObjectRepository.class));
            context.register(FileAccessPolicy.class, FileContentPolicy.class, LocalFileStorageAdapter.class,
                FileCleanupAuditAdapter.class, FileCleanupRetryService.class, FileObjectService.class);

            context.refresh();

            assertNotNull(context.getBean(FileObjectService.class));
            assertNotNull(context.getBean(LocalFileStorageAdapter.class));
            assertNotNull(context.getBean(FileCleanupAuditAdapter.class));
            assertNotNull(context.getBean(FileCleanupRetryService.class));
        }
    }
}
