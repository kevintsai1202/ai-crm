package com.aicrm.crm.bootstrap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.aicrm.crm.service.media.TemporaryMediaService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class TemporaryMediaCleanupJobTest {
    @Test
    void cleanup_retriesParserTempFilesBeforeExpiredMedia() {
        TemporaryMediaService service = mock(TemporaryMediaService.class);
        TemporaryMediaCleanupJob job = new TemporaryMediaCleanupJob(service);

        job.cleanup();

        InOrder order = inOrder(service);
        order.verify(service).retryPendingTempFiles();
        order.verify(service).deleteExpired(any());
    }
}
