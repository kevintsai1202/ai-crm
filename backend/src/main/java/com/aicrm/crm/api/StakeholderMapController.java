package com.aicrm.crm.api;

import com.aicrm.crm.service.JwtService.AuthPrincipal;
import com.aicrm.crm.service.StakeholderMapService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stakeholder 決策鏈 HTTP 邊界（V27）：查詢決策鏈圖、產生 AI 建議、確認 / 拒絕建議與手動新增關係。
 * owner scope 與所有業務規則由服務層統一執行；本層僅處理 HTTP 邊界。
 */
@RestController
@RequestMapping("/api")
public class StakeholderMapController {

    /** 決策鏈應用服務。 */
    private final StakeholderMapService service;

    public StakeholderMapController(StakeholderMapService service) {
        this.service = service;
    }

    /**
     * 取得客戶決策鏈圖：已確認角色 / 關係加上待確認建議（可明確區分）。owner scope。
     *
     * @param id 客戶 id
     * @param principal 登入主體
     * @return 決策鏈回應
     */
    @GetMapping("/customers/{id}/stakeholder-map")
    public Dtos.StakeholderMapResponse getMap(@PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.get(id, principal);
    }

    /**
     * 由現有 Contacts deterministic 產生角色 / 關係建議（狀態 SUGGESTED），回傳待確認清單。owner scope。
     *
     * @param id 客戶 id
     * @param principal 登入主體
     * @return 待確認建議清單
     */
    @PostMapping("/customers/{id}/stakeholder-map/suggest")
    public List<Dtos.StakeholderSuggestionDto> suggest(@PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.suggest(id, principal);
    }

    /**
     * 手動新增一筆已確認關係（來源 MANUAL）；兩位 Contact 必須屬同一客戶。owner scope。
     *
     * @param id 客戶 id
     * @param request 關係建立請求
     * @param principal 登入主體
     * @return 新增的關係 DTO
     */
    @PostMapping("/customers/{id}/stakeholder-map/relations")
    public Dtos.StakeholderRelationDto addRelation(@PathVariable Long id,
            @Valid @RequestBody Dtos.CreateStakeholderRelationRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.addManualRelation(id, request.fromContactId(), request.toContactId(),
                request.relationType(), principal);
    }

    /**
     * 將建議確認為事實（CONFIRMED）。owner scope。
     *
     * @param id 建議 id（role-{id} 或 relation-{id}）
     * @param principal 登入主體
     * @return 更新後的建議 DTO
     */
    @PostMapping("/stakeholder-suggestions/{id}/confirm")
    public Dtos.StakeholderSuggestionDto confirm(@PathVariable String id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.confirm(id, principal);
    }

    /**
     * 拒絕建議（REJECTED，保留稽核）。owner scope。
     *
     * @param id 建議 id（role-{id} 或 relation-{id}）
     * @param principal 登入主體
     * @return 更新後的建議 DTO
     */
    @PostMapping("/stakeholder-suggestions/{id}/reject")
    public Dtos.StakeholderSuggestionDto reject(@PathVariable String id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.reject(id, principal);
    }
}
