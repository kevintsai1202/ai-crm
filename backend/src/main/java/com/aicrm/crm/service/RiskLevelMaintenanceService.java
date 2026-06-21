package com.aicrm.crm.service;

import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.repository.CustomerRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 風險等級維護服務：在交易內以 managed entity 重算並寫回 risk_level 欄位。
 * 以「客戶 ID」為參數而非 Customer 實例，確保在交易內存取 LAZY 互動集合不會 LazyInitialization。
 */
@Service
public class RiskLevelMaintenanceService {

    /** 客戶資料存取。 */
    private final CustomerRepository customers;

    public RiskLevelMaintenanceService(CustomerRepository customers) {
        this.customers = customers;
    }

    /**
     * 重算單一客戶的風險等級並寫回欄位（managed entity，交易結束自動 flush）。
     *
     * @param customerId 客戶 ID
     */
    @Transactional
    public void recompute(Long customerId) {
        var customer = customers.findById(customerId).orElse(null);
        if (customer == null) {
            return;
        }
        customer.applyRiskLevel(computeLevel(customer));
    }

    /**
     * 重算所有客戶的風險等級（每日排程用），單一交易內逐筆處理。
     *
     * @return 重算的客戶數
     */
    @Transactional
    public int recomputeAll() {
        var all = customers.findAll();
        all.forEach(c -> c.applyRiskLevel(computeLevel(c)));
        return all.size();
    }

    /**
     * 依客戶最後互動時間與續約日，以今天為基準計算風險等級。
     *
     * @param customer 客戶（managed，交易內可安全存取互動集合）
     * @return HIGH / MEDIUM / LOW
     */
    private String computeLevel(Customer customer) {
        LocalDateTime last = customer.getInteractions().stream()
                .map(Interaction::getOccurredAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return RiskLevelCalculator.calculate(last, customer.getRenewalDueDate(), LocalDate.now());
    }
}
