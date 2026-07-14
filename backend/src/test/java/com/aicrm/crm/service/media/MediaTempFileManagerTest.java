package com.aicrm.crm.service.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
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

    @Test
    void initialize_rejectsDirectorySymlinkWithoutDeletingExternalFile() throws IOException {
        Path external = root.resolve("external");
        Files.createDirectory(external);
        Path externalFile = external.resolve("ai-crm-33333333-3333-3333-3333-333333333333.wav");
        Files.write(externalFile, new byte[] {1});
        Files.setLastModifiedTime(externalFile, FileTime.from(Instant.now().minus(Duration.ofHours(25))));
        Path configured = root.resolve("configured-link");
        try { Files.createSymbolicLink(configured, external); }
        catch (UnsupportedOperationException | IOException | SecurityException exception) {
            assumeTrue(false, "此執行環境不允許建立 symlink");
        }

        MediaTempFileManager manager = new MediaTempFileManager(configured, Duration.ofHours(24));

        assertThatThrownBy(manager::initialize).isInstanceOf(IOException.class);
        assertThat(externalFile).exists();
    }

    @Test
    void create_rejectsDirectoryIdentityReplacement() throws IOException {
        MediaTempFileManager manager = initializedManager();
        Files.delete(manager.tempDirectory());
        Files.createDirectory(manager.tempDirectory());

        assertThatThrownBy(() -> manager.create(".wav")).isInstanceOf(IOException.class)
                .hasMessage("媒體暫存目錄 identity 已變更");
    }

    @Test
    void posixDirectoryAndFilePermissions_areOwnerOnly() throws IOException {
        MediaTempFileManager manager = initializedManager();
        assumeTrue(Files.getFileStore(manager.tempDirectory()).supportsFileAttributeView("posix"));

        Path created = manager.create(".mp3");

        assertThat(Files.getPosixFilePermissions(manager.tempDirectory()))
                .isEqualTo(PosixFilePermissions.fromString("rwx------"));
        assertThat(Files.getPosixFilePermissions(created))
                .isEqualTo(PosixFilePermissions.fromString("rw-------"));
    }

    @Test
    void constructor_rejectsStaleAgeBelowOneMinute() {
        assertThatThrownBy(() -> new MediaTempFileManager(root.resolve("short"), Duration.ofSeconds(59)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("暫存檔 stale age 不可小於 1 分鐘");
    }

    /** 建立已初始化且使用測試隔離目錄的 manager。 */
    private MediaTempFileManager initializedManager() throws IOException {
        MediaTempFileManager manager = new MediaTempFileManager(root.resolve("dedicated"), Duration.ofHours(24));
        manager.initialize();
        return manager;
    }
}
