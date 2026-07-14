package com.aicrm.crm.service.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 管理媒體解析暫存檔與刪除失敗重試登記。 */
@Component
@ConditionalOnProperty(name = "app.media.enabled", havingValue = "true", matchIfMissing = true)
public class MediaTempFileManager {
    private static final Logger log = LoggerFactory.getLogger(MediaTempFileManager.class);
    /** 等待下一輪清理重試的隨機暫存路徑。 */
    private final Set<Path> pendingDeletes = ConcurrentHashMap.newKeySet();

    /** 建立不含使用者檔名的隨機暫存檔，deleteOnExit 僅作 JVM 結束時最後保險。 */
    public Path create(String suffix) throws IOException {
        Path path = Files.createTempFile("ai-crm-media-", suffix);
        path.toFile().deleteOnExit();
        return path;
    }

    /** 寫入待解析內容。 */
    public void write(Path path, byte[] bytes) throws IOException { Files.write(path, bytes); }

    /** 刪除解析暫存檔。 */
    public void delete(Path path) throws IOException { Files.deleteIfExists(path); }

    /** 登記刪除失敗路徑，供排程重試。 */
    public void registerRetry(Path path) { pendingDeletes.add(path); }

    /** 重試所有待刪除路徑；成功後才從 registry 移除。 */
    public int retryPending() {
        int deleted = 0;
        for (Path path : pendingDeletes) {
            try {
                delete(path);
                if (pendingDeletes.remove(path)) deleted++;
            } catch (IOException | RuntimeException exception) {
                log.warn("媒體驗證暫存檔重試刪除失敗：{}", path, exception);
            }
        }
        return deleted;
    }

    /** 僅供監控與測試取得待刪除數量。 */
    int pendingCount() { return pendingDeletes.size(); }
}
