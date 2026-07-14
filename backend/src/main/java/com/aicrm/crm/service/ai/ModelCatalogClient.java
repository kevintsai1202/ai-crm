package com.aicrm.crm.service.ai;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.AiProvider;
import java.util.List;

/**
 * Provider 模型目錄探索邊界，隔離各家 OpenAI-compatible HTTP 實作。
 */
public interface ModelCatalogClient {

    /**
     * 從 Provider 取得模型與可靠的 input modality metadata。
     *
     * @param provider 含 endpoint 與後端憑證的 Provider
     * @return 可供 Admin 設定的模型清單
     */
    List<Dtos.ModelOptionItem> discover(AiProvider provider);
}
