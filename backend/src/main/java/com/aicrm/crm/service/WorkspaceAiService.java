package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.OpportunityStage;
import com.aicrm.crm.domain.Role;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.service.JwtService.AuthPrincipal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 我的工作檯個人 AI 服務：計算個人待辦、解析資料 scope。
 *
 * <p>串流推薦與問答方法於後續任務補上。資料隔離原則：SALES 一律強制只看自己負責的客戶；
 * MANAGER/ADMIN 預設自己、可切換看全部。所有 scope 解析在後端，前端參數不可信任。</p>
 */
@Service
@Transactional(readOnly = true)
public class WorkspaceAiService {

    /** 即將續約的判定天數。 */
    private static final int RENEWAL_DUE_DAYS = 14;

    /** 客戶資料存取。 */
    private final CustomerRepository customerRepository;

    public WorkspaceAiService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * 載入呼叫者 scope 內的客戶。
     * SALES 一律強制自己（忽略 requestedScope）；MANAGER/ADMIN：requestedScope="all" 回全部，否則回自己。
     *
     * @param principal 認證主體
     * @param requestedScope 前端請求的範圍（self / all）
     * @return scope 內客戶清單
     */
    List<Customer> loadScopedCustomers(AuthPrincipal principal, String requestedScope) {
        boolean all = principal.role() != Role.SALES && "all".equalsIgnoreCase(requestedScope);
        return all ? customerRepository.findAll()
                   : customerRepository.findByOwnerName(principal.displayName());
    }

    /**
     * 以純 DB 規則計算個人待辦：高風險、即將續約（14 天內）、逾期未結商機。
     * 不依賴 AI，永遠可用，並作為 AI 總結的 grounding。
     *
     * @param principal 認證主體
     * @param scope 請求範圍（SALES 會被強制為自己）
     * @return 待辦清單
     */
    public List<Dtos.WorkspaceTodoItem> computeTodos(AuthPrincipal principal, String scope) {
        var customers = loadScopedCustomers(principal, scope);
        var today = LocalDate.now();
        var todos = new ArrayList<Dtos.WorkspaceTodoItem>();
        for (var c : customers) {
            // 高風險客戶
            if ("HIGH".equalsIgnoreCase(c.getRiskLevel())) {
                todos.add(new Dtos.WorkspaceTodoItem("HIGH_RISK", c.getId(), c.getName(),
                        "客戶風險等級為高，建議優先聯繫關懷", "HIGH"));
            }
            // 即將續約（今日起 14 天內）
            var due = c.getRenewalDueDate();
            if (due != null && !due.isBefore(today) && !due.isAfter(today.plusDays(RENEWAL_DUE_DAYS))) {
                todos.add(new Dtos.WorkspaceTodoItem("RENEWAL_DUE", c.getId(), c.getName(),
                        "續約日 " + due + " 即將到期，建議啟動續約", "MEDIUM"));
            }
            // 逾期未結商機（開放中且預計成交日早於今日）
            for (var o : c.getOpportunities()) {
                boolean open = o.getStage() != OpportunityStage.CLOSED_WON
                        && o.getStage() != OpportunityStage.CLOSED_LOST;
                if (open && o.getExpectedCloseDate() != null && o.getExpectedCloseDate().isBefore(today)) {
                    todos.add(new Dtos.WorkspaceTodoItem("STALE_OPPORTUNITY", c.getId(), c.getName(),
                            "商機「" + o.getName() + "」預計成交日已過（" + o.getExpectedCloseDate() + "），需推進或結案", "MEDIUM"));
                }
            }
        }
        return todos;
    }
}
