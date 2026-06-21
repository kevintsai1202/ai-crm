package com.aicrm.crm.bootstrap;

import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.service.RiskLevelMaintenanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 啟動補算風險等級：為 risk_level 為 null 的客戶（例如 Flyway 種子或舊資料）逐筆重算。
 * @Order(4)：排在情緒意圖補算（@Order(3)）之後。冪等：補過的不再為 null，重跑不重算。
 */
@Component
@Order(4)
public class RiskLevelBackfillRunner implements ApplicationRunner {

    /** 記錄補算筆數。 */
    private static final Logger log = LoggerFactory.getLogger(RiskLevelBackfillRunner.class);

    /** 客戶資料存取。 */
    private final CustomerRepository customers;

    /** 風險等級重算服務。 */
    private final RiskLevelMaintenanceService maintenance;

    public RiskLevelBackfillRunner(CustomerRepository customers, RiskLevelMaintenanceService maintenance) {
        this.customers = customers;
        this.maintenance = maintenance;
    }

    /**
     * 啟動時補算缺漏的風險等級。
     *
     * @param args 應用參數（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        var ids = customers.findIdsByRiskLevelIsNull();
        ids.forEach(maintenance::recompute);
        if (!ids.isEmpty()) {
            log.info("啟動補算風險等級完成：{} 筆", ids.size());
        }
    }
}
