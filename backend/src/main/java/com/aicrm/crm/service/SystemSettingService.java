package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.AiProvider;
import com.aicrm.crm.domain.SystemSetting;
import com.aicrm.crm.repository.AiProviderRepository;
import com.aicrm.crm.repository.SystemSettingRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 系統設定服務：提供 AI 對話模型設定的讀寫、供應商管理（Provider CRUD）、與回退解析。
 * 函式級註解：currentModel 空字串代表「使用環境變數預設」；resolveChatOptions() 空時回 null，
 * 呼叫端不覆蓋 ChatClient options → 沿用 Spring AI 由環境變數初始化的預設模型。
 */
@Service
public class SystemSettingService {

    private static final Logger log = LoggerFactory.getLogger(SystemSettingService.class);

    /** 目前選用模型設定鍵。 */
    public static final String KEY_AI_CHAT_MODEL = "ai.chat.model";
    /** 可選模型清單設定鍵（值為 ModelOptionItem JSON 陣列）。 */
    public static final String KEY_AI_CHAT_MODEL_OPTIONS = "ai.chat.model_options";
    /** 目前選用 provider ID 設定鍵（空字串 = 未指定）。 */
    public static final String KEY_AI_CHAT_PROVIDER_ID = "ai.chat.provider_id";

    /** 設定資料存取。 */
    private final SystemSettingRepository repository;
    /** AI 供應商資料存取。 */
    private final AiProviderRepository providerRepository;
    /** Jackson 3 ObjectMapper（Spring Boot 4.1 受管 bean）。 */
    private final ObjectMapper objectMapper;
    /** 環境變數預設模型（spring.ai.openai.chat.model），供留空時在 UI 顯示回退目標。 */
    private final String envDefaultModel;

    public SystemSettingService(SystemSettingRepository repository,
                                AiProviderRepository providerRepository,
                                ObjectMapper objectMapper,
                                @Value("${spring.ai.openai.chat.model:}") String envDefaultModel) {
        this.repository = repository;
        this.providerRepository = providerRepository;
        this.objectMapper = objectMapper;
        this.envDefaultModel = envDefaultModel;
    }

    /**
     * 取得目前選用模型；空字串視為未設定（回退環境變數）。
     *
     * @return 模型名 Optional（未設定或空字串為 empty）
     */
    @Transactional(readOnly = true)
    public Optional<String> getAiChatModel() {
        return repository.findBySettingKey(KEY_AI_CHAT_MODEL)
                .map(SystemSetting::getSettingValue)
                .filter(StringUtils::hasText);
    }

    /**
     * 取得目前選用的 provider ID（空字串視為未設定）。
     *
     * @return provider ID Optional
     */
    @Transactional(readOnly = true)
    public Optional<Long> getCurrentProviderId() {
        return repository.findBySettingKey(KEY_AI_CHAT_PROVIDER_ID)
                .map(SystemSetting::getSettingValue)
                .filter(StringUtils::hasText)
                .map(Long::valueOf);
    }

    /**
     * 取得可選模型清單（含 provider 關聯）；解析失敗回空清單。
     *
     * @return ModelOptionItem 清單
     */
    @Transactional(readOnly = true)
    public List<Dtos.ModelOptionItem> getModelOptions() {
        return repository.findBySettingKey(KEY_AI_CHAT_MODEL_OPTIONS)
                .map(SystemSetting::getSettingValue)
                .map(this::parseModelOptions)
                .orElseGet(List::of);
    }

    /**
     * 回傳目前模型對應的 ChatOptions Builder；未設定模型時回 null（呼叫端不覆蓋，沿用環境變數預設）。
     *
     * @return OpenAiChatOptions.Builder 或 null
     */
    @Transactional(readOnly = true)
    public OpenAiChatOptions.Builder resolveChatOptions() {
        return getAiChatModel()
                .map(m -> OpenAiChatOptions.builder().model(m))
                .orElse(null);
    }

    /**
     * 取得所有 provider 檢視清單（apiKey 以 apiKeySet 布林代替，不回傳原文）。
     *
     * @return provider 檢視清單
     */
    @Transactional(readOnly = true)
    public List<Dtos.AiProviderItem> getProviders() {
        return providerRepository.findAll().stream()
                .map(p -> new Dtos.AiProviderItem(p.getId(), p.getName(), p.getBaseUrl(), p.isApiKeySet()))
                .toList();
    }

    /**
     * 以 id 取得 provider entity（含 apiKey），供 InsightService 動態建立 ChatModel 使用。
     *
     * @param id provider id
     * @return AiProvider Optional
     */
    @Transactional(readOnly = true)
    public Optional<AiProvider> findProviderById(Long id) {
        return providerRepository.findById(id);
    }

    /**
     * 新增 provider；name 重複回例外。
     *
     * @param req 請求 DTO
     * @param username 操作者帳號
     * @return 新增後的 provider 檢視
     */
    @Transactional
    public Dtos.AiProviderItem createProvider(Dtos.AiProviderRequest req, String username) {
        if (providerRepository.existsByName(req.name())) {
            throw new IllegalArgumentException("供應商名稱已存在：" + req.name());
        }
        var provider = new AiProvider(req.name(), req.baseUrl(), req.apiKey(), username);
        var saved = providerRepository.save(provider);
        return new Dtos.AiProviderItem(saved.getId(), saved.getName(), saved.getBaseUrl(), saved.isApiKeySet());
    }

    /**
     * 更新 provider；apiKey 為 null 或空字串時保留現有金鑰；name 重複（排除自身）回例外。
     *
     * @param id provider id
     * @param req 請求 DTO
     * @param username 操作者帳號
     * @return 更新後的 provider 檢視
     */
    @Transactional
    public Dtos.AiProviderItem updateProvider(Long id, Dtos.AiProviderRequest req, String username) {
        var provider = providerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Provider 不存在：" + id));
        if (providerRepository.existsByNameAndIdNot(req.name(), id)) {
            throw new IllegalArgumentException("供應商名稱已存在：" + req.name());
        }
        provider.update(req.name(), req.baseUrl(), req.apiKey(), username);
        var saved = providerRepository.save(provider);
        return new Dtos.AiProviderItem(saved.getId(), saved.getName(), saved.getBaseUrl(), saved.isApiKeySet());
    }

    /**
     * 刪除 provider。
     *
     * @param id provider id
     */
    @Transactional
    public void deleteProvider(Long id) {
        if (!providerRepository.existsById(id)) {
            throw new IllegalArgumentException("Provider 不存在：" + id);
        }
        providerRepository.deleteById(id);
    }

    /**
     * 組裝 AI 設定檢視（含供應商清單與帶 providerId 的模型選項）。
     *
     * @return AI 設定回應 DTO
     */
    @Transactional(readOnly = true)
    public Dtos.AiSettingsResponse getAiSettingsView() {
        var current = getAiChatModel().orElse("");
        var currentProviderId = getCurrentProviderId().orElse(null);
        var options = getModelOptions();
        var providers = getProviders();
        var source = current.isBlank() ? "ENV" : "DB";
        return new Dtos.AiSettingsResponse(current, currentProviderId, options, providers, envDefaultModel, source);
    }

    /**
     * upsert AI 設定：model 須為空或在 options 內，否則拋例外。
     *
     * @param model 選用模型名稱（空=用環境變數）
     * @param providerId 選用 provider ID（null=清空）
     * @param modelOptions 候選清單（含 provider 關聯）
     * @param username 操作者帳號
     */
    @Transactional
    public void updateAiSettings(String model, Long providerId,
                                 List<Dtos.ModelOptionItem> modelOptions, String username) {
        var safeModel = model == null ? "" : model.strip();
        var safeOptions = modelOptions == null ? List.<Dtos.ModelOptionItem>of() : modelOptions;
        var modelNames = safeOptions.stream().map(Dtos.ModelOptionItem::model).toList();
        if (!safeModel.isBlank() && !modelNames.contains(safeModel)) {
            throw new IllegalArgumentException("選用模型不在候選清單內：" + safeModel);
        }
        upsert(KEY_AI_CHAT_MODEL, safeModel, username);
        upsert(KEY_AI_CHAT_PROVIDER_ID, providerId != null ? providerId.toString() : "", username);
        upsert(KEY_AI_CHAT_MODEL_OPTIONS, serializeModelOptions(safeOptions), username);
    }

    /** 解析 ModelOptionItem 清單 JSON；任何錯誤回空清單並記 log。 */
    private List<Dtos.ModelOptionItem> parseModelOptions(String json) {
        try {
            if (!StringUtils.hasText(json)) return List.of();
            return objectMapper.readValue(json, new TypeReference<List<Dtos.ModelOptionItem>>() {});
        } catch (Exception e) {
            log.warn("模型清單 JSON 解析失敗，回空清單：{}", e.getMessage());
            return List.of();
        }
    }

    /** 序列化 ModelOptionItem 清單為 JSON 字串；序列化失敗包成 IllegalStateException 拋出。 */
    private String serializeModelOptions(List<Dtos.ModelOptionItem> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            throw new IllegalStateException("模型清單序列化失敗", e);
        }
    }

    /** upsert 單一設定鍵。 */
    private void upsert(String key, String value, String username) {
        repository.findBySettingKey(key).ifPresentOrElse(
                existing -> { existing.updateValue(value, username); repository.save(existing); },
                () -> repository.save(new SystemSetting(key, value, username))
        );
    }
}
