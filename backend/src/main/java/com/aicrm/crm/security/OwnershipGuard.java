package com.aicrm.crm.security;

import com.aicrm.crm.domain.Role;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.service.JwtService.AuthPrincipal;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 資源擁有權守衛：強制 SALES 角色只能存取自己負責（ownerName 相符）的客戶相關資源，
 * 補上「已認證但跨業務水平越權（IDOR）」的資源層防線。
 *
 * <p>主體由 {@link SecurityContextHolder} 取得（JwtAuthenticationFilter 已於請求執行緒設定），
 * 因此不需污染各 service / controller 的方法簽章。所有受保護端點皆在請求執行緒內完成資源載入與檢查，
 * SecurityContext 必為可用。</p>
 *
 * <p>放行策略：僅在「已認證、角色為 SALES、且資源 ownerName 與主體顯示名稱不符」時丟出
 * {@link AccessDeniedException}（由 GlobalExceptionHandler 轉為 403）。無認證主體（單元測試、系統流程）
 * 或 MANAGER / ADMIN 角色一律放行，與既有列表查詢 scope（CustomerService.buildSpec）語意一致。</p>
 */
@Component
public class OwnershipGuard {

    /** 客戶資料存取：以 customerId 反查負責業務時使用。 */
    private final CustomerRepository customers;

    public OwnershipGuard(CustomerRepository customers) {
        this.customers = customers;
    }

    /**
     * 取得當前登入主體。
     *
     * @return 登入主體，未認證時回 null
     */
    private AuthPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        return null;
    }

    /**
     * 驗證當前使用者可存取由指定 ownerName 負責的資源；SALES 角色存取他人資源時丟 403。
     *
     * @param ownerName 資源的負責業務顯示名稱
     */
    public void assertCanAccessOwner(String ownerName) {
        var principal = currentPrincipal();
        if (principal != null && principal.role() == Role.SALES
                && !Objects.equals(principal.displayName(), ownerName)) {
            throw new AccessDeniedException("無權存取非本人負責的客戶資料");
        }
    }

    /**
     * 以 customerId 反查負責業務後做擁有權驗證；查無客戶時不阻擋（交由下游回 404 / 空結果）。
     *
     * @param customerId 客戶 ID
     */
    public void assertCanAccessCustomer(Long customerId) {
        if (customerId == null) {
            return;
        }
        customers.findById(customerId).ifPresent(customer -> assertCanAccessOwner(customer.getOwnerName()));
    }
}
