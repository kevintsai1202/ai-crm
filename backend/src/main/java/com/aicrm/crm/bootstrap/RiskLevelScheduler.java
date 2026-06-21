package com.aicrm.crm.bootstrap;

import com.aicrm.crm.service.RiskLevelMaintenanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日風險等級重算排程。預設啟用（matchIfMissing=true），可由
 * app.risk.daily-recompute.enabled=false 關閉。每日凌晨 03:00 重算全表。
 */
@Component
@ConditionalOnProperty(name = "app.risk.daily-recompute.enabled", havingValue = "true", matchIfMissing = true)
public class RiskLevelScheduler {

    /** 記錄每日重算結果。 */
    private static final Logger log = LoggerFactory.getLogger(RiskLevelScheduler.class);

    /** 風險等級重算服務。 */
    private final RiskLevelMaintenanceService maintenance;

    public RiskLevelScheduler(RiskLevelMaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    /**
     * 每日凌晨 03:00 重算所有客戶風險等級。
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void recomputeDaily() {
        int n = maintenance.recomputeAll();
        log.info("每日風險等級重算完成：{} 筆", n);
    }
}
