package com.aicrm.crm.api;

import com.aicrm.crm.service.DemoDataService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 開發 / 示範用 REST API（僅限 ADMIN；正式 profile 不註冊此 Controller）。
 *
 * <p>提供大量示範資料生成端點，灌入客戶 / 互動 / 商機與情緒意圖分析結果，
 * 讓儀表板情緒雷達有足夠樣本可視覺化。權限由 SecurityConfig 以 {@code /api/dev/**} → ADMIN 控管。</p>
 */
@RestController
@RequestMapping("/api/dev")
@Profile("!prod")
public class DevController {

    /** 示範資料生成服務。 */
    private final DemoDataService demoDataService;

    public DevController(DemoDataService demoDataService) {
        this.demoDataService = demoDataService;
    }

    /**
     * 生成示範資料。預設清除重建（reset=true），先清掉既有業務資料再重建，
     * 使漏斗等報表呈現乾淨的示範分布；reset=false 則沿用附加模式。
     *
     * <p>清除重建需後端啟用 {@code app.demo.reset-enabled=true}（正式環境應維持關閉）。</p>
     *
     * @param customers 欲生成的客戶數（預設 200）
     * @param reset 是否先清除既有業務資料再重建（預設 true）
     * @return 生成統計（客戶數 / 互動數 / 分析筆數）
     */
    @PostMapping("/generate-demo-data")
    public DemoDataService.DemoStats generateDemoData(
            @RequestParam(defaultValue = "200") int customers,
            @RequestParam(defaultValue = "true") boolean reset) {
        return demoDataService.generate(customers, reset);
    }
}
