package com.aicrm.crm.bootstrap;

import com.aicrm.crm.service.media.TemporaryMediaService;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期清除逾期且尚未確認的暫存媒體。 */
@Component
@ConditionalOnProperty(name = {"app.media.enabled", "app.media.cleanup.enabled"}, havingValue = "true", matchIfMissing = true)
public class TemporaryMediaCleanupJob {
    /** 暫存媒體服務。 */
    private final TemporaryMediaService mediaService;

    /** 建立暫存媒體排程。 */
    public TemporaryMediaCleanupJob(TemporaryMediaService mediaService) {
        this.mediaService = mediaService;
    }

    /** 依可調整 fixed delay 執行到期清理；服務內逐筆隔離失敗。 */
    @Scheduled(fixedDelayString = "${app.media.cleanup.fixed-delay:PT1H}")
    public void cleanup() {
        mediaService.retryPendingTempFiles();
        mediaService.deleteExpired(Instant.now());
    }
}
