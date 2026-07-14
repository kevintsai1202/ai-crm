package com.aicrm.crm.service.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaTempFileManagerTest {
    @TempDir
    Path root;

    @Test
    void initialize_removesOnlyOldManagedRegularFiles() throws IOException {
        Path directory = root.resolve("dedicated");
        Files.createDirectories(directory);
        Path stale = directory.resolve("ai-crm-11111111-1111-1111-1111-111111111111.wav");
        Path recent = directory.resolve("ai-crm-22222222-2222-2222-2222-222222222222.mp3");
        Path foreign = directory.resolve("customer-audio.wav");
        Files.write(stale, new byte[] {1});
        Files.write(recent, new byte[] {1});
        Files.write(foreign, new byte[] {1});
        Files.setLastModifiedTime(stale, FileTime.from(Instant.now().minus(Duration.ofHours(25))));

        MediaTempFileManager manager = new MediaTempFileManager(directory, Duration.ofHours(24));
        manager.initialize();

        assertThat(stale).doesNotExist();
        assertThat(recent).exists();
        assertThat(foreign).exists();
    }

    @Test
    void createAndDelete_useDedicatedUuidPathAndLeaveNoRegistryEntry() throws IOException {
        MediaTempFileManager manager = initializedManager();

        Path created = manager.create(".wav");
        manager.write(created, new byte[] {1, 2, 3});

        assertThat(created.getParent()).isEqualTo(manager.tempDirectory());
        assertThat(created.getFileName().toString()).matches("ai-crm-[0-9a-f-]{36}\\.wav");
        assertThat(manager.ownedCount()).isEqualTo(1);
        manager.delete(created);
        assertThat(created).doesNotExist();
        assertThat(manager.ownedCount()).isZero();
        assertThat(manager.pendingCount()).isZero();
    }

    @Test
    void registryRejectsTraversalAndShutdownDeletesOwnedFiles() throws IOException {
        MediaTempFileManager manager = initializedManager();
        Path outside = root.resolve("outside.wav");
        Files.write(outside, new byte[] {1});
        Path owned = manager.create(".m4a");

        assertThatThrownBy(() -> manager.registerRetry(outside)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> manager.delete(manager.tempDirectory().resolve("..\\outside.wav")))
                .isInstanceOf(IllegalArgumentException.class);

        manager.shutdownCleanup();
        assertThat(owned).doesNotExist();
        assertThat(manager.ownedCount()).isZero();
        assertThat(manager.pendingCount()).isZero();
        assertThat(outside).exists();
    }

    /** 建立已初始化且使用測試隔離目錄的 manager。 */
    private MediaTempFileManager initializedManager() throws IOException {
        MediaTempFileManager manager = new MediaTempFileManager(root.resolve("dedicated"), Duration.ofHours(24));
        manager.initialize();
        return manager;
    }
}
