package com.aicrm.crm.api;

import com.aicrm.crm.service.DashboardService;
import com.aicrm.crm.service.RfmService;
import com.aicrm.crm.service.SentimentService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard API，提供前端首頁統計卡片資料。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    /** Dashboard 聚合服務。 */
    private final DashboardService dashboardService;

    /** RFM 客戶分群服務。 */
    private final RfmService rfmService;

    /** 情緒意圖雷達聚合服務。 */
    private final SentimentService sentimentService;

    public DashboardController(DashboardService dashboardService, RfmService rfmService, SentimentService sentimentService) {
        this.dashboardService = dashboardService;
        this.rfmService = rfmService;
        this.sentimentService = sentimentService;
    }

    /**
     * 回傳 CRM Dashboard 統計。
     *
     * @return Dashboard 摘要
     */
    @GetMapping("/summary")
    public Dtos.DashboardSummary summary() {
        return dashboardService.dashboardSummary();
    }

    /**
     * 回傳 CRM 經典圖表報表資料。
     *
     * @return Dashboard 報表資料
     */
    @GetMapping("/reports")
    public Dtos.DashboardReports reports() {
        return dashboardService.dashboardReports();
    }

    /**
     * 圖表下鑽明細：依圖表類型與鍵值列出底層商機或客戶。
     *
     * @param type 下鑽類型（stage / forecastMonth / renewalMonth / industry / owner / risk）
     * @param key 對應鍵值
     * @return 下鑽明細
     */
    @GetMapping("/drilldown")
    public Dtos.DrilldownResponse drilldown(@RequestParam String type, @RequestParam String key) {
        return dashboardService.drilldown(type, key);
    }

    /**
     * 回傳每位客戶的 RFM 分數與分群標籤。
     *
     * @return RFM 分群結果清單
     */
    @GetMapping("/rfm")
    public List<Dtos.RfmResponse> rfm() {
        return rfmService.computeRfm();
    }

    /**
     * 回傳情緒意圖雷達聚合（意圖分布、情緒趨勢、高風險互動、流失雷達、優先關懷）。
     *
     * @return 情緒意圖雷達結果
     */
    @GetMapping("/sentiment")
    public Dtos.SentimentRadarResponse sentiment() {
        return sentimentService.radar();
    }
}
