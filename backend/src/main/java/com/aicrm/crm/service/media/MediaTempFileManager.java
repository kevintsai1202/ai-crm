package com.aicrm.crm.service.media;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 管理專屬目錄內的媒體解析暫存檔及其完整生命週期。 */
@Component
@ConditionalOnProperty(name = "app.media.enabled", havingValue = "true", matchIfMissing = true)
public class MediaTempFileManager {
    private static final Logger log = LoggerFactory.getLogger(MediaTempFileManager.class);
    private static final Pattern MANAGED_NAME = Pattern.compile("ai-crm-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(mp3|m4a|wav)");
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = PosixFilePermissions.fromString("rw-------");
    /** 經正規化的媒體專屬暫存目錄。 */
    private final Path tempDirectory;
    /** 啟動時可清除的 crash 遺留檔最小年齡。 */
    private final Duration staleAge;
    /** 本 process 建立且尚未成功刪除的路徑。 */
    private final Set<Path> ownedPaths = ConcurrentHashMap.newKeySet();
    /** 等待下一輪清理重試的路徑。 */
    private final Set<Path> pendingDeletes = ConcurrentHashMap.newKeySet();
    /** 初始化後固定的目錄 real path，用來偵測替換或重新導向。 */
    private Path directoryRealPath;
    /** 初始化後固定的 filesystem identity；平台不提供時為 null。 */
    private Object directoryFileKey;
    /** fileKey 不可用時補充比對的目錄建立時間。 */
    private FileTime directoryCreationTime;
    /** 目前 filesystem 是否支援 POSIX permissions。 */
    private boolean posixPermissions;

    /** 由 Spring 設定專屬目錄與 stale age。 */
    @Autowired
    public MediaTempFileManager(
            @Value("${app.media.temp-dir:${java.io.tmpdir}/ai-crm-media}") String tempDirectory,
            @Value("${app.media.temp-stale-age:PT24H}") Duration staleAge) {
        this(Path.of(tempDirectory), staleAge);
    }

    /** 供測試以隔離目錄建立 manager。 */
    MediaTempFileManager(Path tempDirectory, Duration staleAge) {
        this.tempDirectory = tempDirectory.toAbsolutePath().normalize();
        if (staleAge.compareTo(Duration.ofMinutes(1)) < 0) throw new IllegalArgumentException("暫存檔 stale age 不可小於 1 分鐘");
        this.staleAge = staleAge;
    }

    /** 建立專屬目錄，並清除符合命名且超齡的 crash 遺留一般檔案。 */
    @PostConstruct
    public void initialize() throws IOException {
        Path configuredParent = tempDirectory.getParent();
        if (configuredParent == null) throw new IllegalArgumentException("媒體暫存目錄必須有父目錄");
        Files.createDirectories(configuredParent);
        rejectDirectoryLinkOrNonDirectoryIfPresent();
        posixPermissions = Files.getFileStore(configuredParent).supportsFileAttributeView("posix");
        if (!Files.exists(tempDirectory, LinkOption.NOFOLLOW_LINKS)) {
            if (posixPermissions) Files.createDirectory(tempDirectory, PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS));
            else Files.createDirectory(tempDirectory);
        }
        validateAndCaptureDirectoryIdentity(configuredParent.toRealPath());
        if (posixPermissions) Files.setPosixFilePermissions(tempDirectory, DIRECTORY_PERMISSIONS);
        cleanupStaleFiles();
    }

    /** 掃除符合命名與年齡條件的 crash 遺留檔；掃描前重新確認目錄未被替換。 */
    private void cleanupStaleFiles() throws IOException {
        validateDirectoryIdentity();
        Instant cutoff = Instant.now().minus(staleAge);
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(tempDirectory)) {
            for (Path entry : entries) {
                Path normalized = entry.toAbsolutePath().normalize();
                if (!isDirectChild(normalized) || !MANAGED_NAME.matcher(normalized.getFileName().toString()).matches()) continue;
                if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized)) continue;
                FileTime modified = Files.getLastModifiedTime(normalized, LinkOption.NOFOLLOW_LINKS);
                if (modified.toInstant().isBefore(cutoff)) Files.deleteIfExists(normalized);
            }
        }
    }

    /** 以 UUID 名稱在專屬目錄建立不含使用者資料的暫存檔。 */
    public Path create(String suffix) throws IOException {
        validateDirectoryIdentity();
        String safeSuffix = switch (suffix) {
            case ".mp3", ".m4a", ".wav" -> suffix;
            default -> throw new IllegalArgumentException("不支援的媒體暫存副檔名");
        };
        Path path = tempDirectory.resolve("ai-crm-" + UUID.randomUUID() + safeSuffix).normalize();
        if (!isDirectChild(path)) throw new IllegalStateException("媒體暫存路徑超出專屬目錄");
        if (posixPermissions) Files.createFile(path, PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS));
        else Files.createFile(path);
        ownedPaths.add(path);
        return path;
    }

    /** 寫入本 process 擁有的待解析內容。 */
    public void write(Path path, byte[] bytes) throws IOException {
        Path managed = requireOwned(path);
        Files.write(managed, bytes);
    }

    /** 刪除本 process 擁有的檔案；成功時同步移除 owned/pending registry。 */
    public void delete(Path path) throws IOException {
        Path managed = requireOwned(path);
        Files.deleteIfExists(managed);
        pendingDeletes.remove(managed);
        ownedPaths.remove(managed);
    }

    /** 登記刪除失敗路徑；拒絕外部、越界或非本 process 擁有的路徑。 */
    public void registerRetry(Path path) { pendingDeletes.add(requireOwned(path)); }

    /** 重試所有待刪除路徑；成功後由 delete 同時移除兩個 registry。 */
    public int retryPending() {
        int deleted = 0;
        for (Path path : Set.copyOf(pendingDeletes)) {
            try { delete(path); deleted++; }
            catch (IOException | RuntimeException exception) { log.warn("媒體驗證暫存檔重試刪除失敗：{}", path, exception); }
        }
        return deleted;
    }

    /** 關機時把全部 owned 路徑納入 pending，並做最後一次同步清理。 */
    @PreDestroy
    public void shutdownCleanup() {
        pendingDeletes.addAll(ownedPaths);
        retryPending();
    }

    /** 驗證路徑為專屬目錄直屬且由本 process 建立。 */
    private Path requireOwned(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!isDirectChild(normalized) || !ownedPaths.contains(normalized)) throw new IllegalArgumentException("非本 process 擁有的媒體暫存路徑");
        return normalized;
    }

    /** 確認路徑只位於專屬目錄的第一層，避免 traversal 與跨目錄操作。 */
    private boolean isDirectChild(Path path) { return tempDirectory.equals(path.getParent()); }

    /** 初始化前拒絕 configured directory 本身為 symlink 或非一般目錄。 */
    private void rejectDirectoryLinkOrNonDirectoryIfPresent() throws IOException {
        if (!Files.exists(tempDirectory, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(tempDirectory) || !Files.isDirectory(tempDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("媒體暫存目錄不可為 symlink 或非目錄");
        }
    }

    /** 建立後確認 real directory 的 parent 與 configured parent 相同，並保存 filesystem identity。 */
    private void validateAndCaptureDirectoryIdentity(Path expectedParentReal) throws IOException {
        rejectDirectoryLinkOrNonDirectoryIfPresent();
        Path real = tempDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!expectedParentReal.equals(real.getParent())) throw new IOException("媒體暫存目錄 real path 超出 configured parent");
        BasicFileAttributes attributes = Files.readAttributes(tempDirectory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        directoryRealPath = real;
        directoryFileKey = attributes.fileKey();
        directoryCreationTime = attributes.creationTime();
    }

    /** 每次 create/scan 前重驗目錄 symlink、real path 與 file identity，降低替換型 TOCTOU。 */
    private void validateDirectoryIdentity() throws IOException {
        rejectDirectoryLinkOrNonDirectoryIfPresent();
        Path currentReal = tempDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS);
        BasicFileAttributes attributes = Files.readAttributes(tempDirectory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!currentReal.equals(directoryRealPath)
                || directoryFileKey != null && !directoryFileKey.equals(attributes.fileKey())
                || directoryFileKey == null && !directoryCreationTime.equals(attributes.creationTime())) {
            throw new IOException("媒體暫存目錄 identity 已變更");
        }
    }

    /** 僅供監控與測試取得待刪除數量。 */
    int pendingCount() { return pendingDeletes.size(); }
    /** 僅供監控與測試取得本 process 尚未刪除數量。 */
    int ownedCount() { return ownedPaths.size(); }
    /** 僅供測試確認專屬目錄。 */
    Path tempDirectory() { return tempDirectory; }
}
