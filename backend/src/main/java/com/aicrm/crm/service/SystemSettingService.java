package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.SystemSetting;
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
 * 系統設定服務：目前提供「AI 對話模型」設定的讀寫與回退解析。
 * 函式級註解：currentModel 空字串代表「使用環境變數預設」；resolveChatOptions() 空時回 null，
 * 呼叫端不覆蓋 ChatClient options → 沿用 Spring AI 由環境變數初始化的預設模型。
 */
@Service
public class SystemSettingService {

    private static final Logger log = LoggerFactory.getLogger(SystemSettingService.class);

    /** 目前選用模型設定鍵。 */
    public static final String KEY_AI_CHAT_MODEL = "ai.chat.model";
    /** 可選模型清單設定鍵。 */
    public static final String KEY_AI_CHAT_MODEL_OPTIONS = "ai.chat.model_options";

    /** 設定資料存取。 */
    private final SystemSettingRepository repository;
    /** Jackson 3 ObjectMapper（Spring Boot 4.1 受管 bean）。 */
    private final ObjectMapper objectMapper;
    /** 環境變數預設模型（spring.ai.openai.chat.model），供留空時在 UI 顯示回退目標。 */
    private final String envDefaultModel;

    public SystemSettingService(SystemSettingRepository repository,
                                ObjectMapper objectMapper,
                                @Value("${spring.ai.openai.chat.model:}") String envDefaultModel) {
        this.repository = repository;
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
     * 取得可選模型清單；解析失敗回空清單。
     *
     * @return 模型名清單
     */
    @Transactional(readOnly = true)
    public List<String> getModelOptions() {
        return repository.findBySettingKey(KEY_AI_CHAT_MODEL_OPTIONS)
                .map(SystemSetting::getSettingValue)
                .map(this::parseOptions)
                .orElseGet(List::of);
    }

    /**
     * 回傳目前模型對應的 ChatOptions Builder；未設定模型時回 null（呼叫端不覆蓋，沿用環境變數預設）。
     * 函式級註解：Spring AI 2.0 ChatClientRequestSpec.options() 接受 Builder 而非 built ChatOptions，故此處回 Builder。
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
     * 組裝 AI 設定檢視（供前端顯示）。
     *
     * @return 含 currentModel、modelOptions、envDefaultModel、source（DB/ENV）
     */
    @Transactional(readOnly = true)
    public Dtos.AiSettingsResponse getAiSettingsView() {
        var current = getAiChatModel().orElse("");
        var options = getModelOptions();
        var source = current.isBlank() ? "ENV" : "DB";
        return new Dtos.AiSettingsResponse(current, options, envDefaultModel, source);
    }

    /**
     * upsert AI 設定：model 須為空或在 options 內，否則拋例外；兩個 key 各 upsert。
     *
     * @param model 選用模型（空字串=用環境變數）
     * @param modelOptions 候選清單
     * @param username 操作者帳號
     */
    @Transactional
    public void updateAiSettings(String model, List<String> modelOptions, String username) {
        var safeModel = model == null ? "" : model.strip();
        var safeOptions = modelOptions == null ? List.<String>of() : modelOptions;
        if (!safeModel.isBlank() && !safeOptions.contains(safeModel)) {
            throw new IllegalArgumentException("選用模型不在候選清單內：" + safeModel);
        }
        upsert(KEY_AI_CHAT_MODEL, safeModel, username);
        upsert(KEY_AI_CHAT_MODEL_OPTIONS, serializeOptions(safeOptions), username);
    }

    /** 解析模型清單 JSON；任何錯誤回空清單並記 log。 */
    private List<String> parseOptions(String json) {
        try {
            if (!StringUtils.hasText(json)) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (RuntimeException e) {
            log.warn("模型清單 JSON 解析失敗，回空清單：{}", e.getMessage());
            return List.of();
        }
    }

    /** 序列化模型清單為 JSON 字串。 */
    private String serializeOptions(List<String> options) {
        return objectMapper.writeValueAsString(options);
    }

    /** upsert 單一設定鍵。 */
    private void upsert(String key, String value, String username) {
        repository.findBySettingKey(key).ifPresentOrElse(
                existing -> { existing.updateValue(value, username); repository.save(existing); },
                () -> repository.save(new SystemSetting(key, value, username))
        );
    }
}
