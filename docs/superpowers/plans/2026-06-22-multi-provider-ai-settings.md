# Multi-Provider AI Settings 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 讓 ADMIN 在 DB 中管理多組 AI 供應商（name / baseUrl / apiKey），模型選項關聯至供應商，競速測試以各模型的供應商憑證動態建立 ChatModel。

**Architecture:** 新增 `ai_providers` table；`ai.chat.model_options` 從 `["model"]` 遷移至 `[{model, providerId}]`；`AiTestRequest` 帶入 `providerId`，`InsightService.streamModelTest` 依此動態建立 `OpenAiApi` + `OpenAiChatModel`；apiKey 僅後端讀取，GET 回應以 `apiKeySet: boolean` 代替。

**Tech Stack:** Spring Boot 4.1 + Java 21 + Spring AI 2.0 (`OpenAiApi`/`OpenAiChatModel` programmatic instantiation) + React 19 + TypeScript + Flyway + PostgreSQL

---

## 異動檔案地圖

**後端 — 新增：**
- `backend/src/main/java/com/aicrm/crm/domain/AiProvider.java`
- `backend/src/main/java/com/aicrm/crm/repository/AiProviderRepository.java`
- `backend/src/main/resources/db/migration/V17__add_ai_providers.sql`

**後端 — 修改：**
- `backend/src/main/java/com/aicrm/crm/api/Dtos.java`（約 427–447 行 AI DTO 區塊）
- `backend/src/main/java/com/aicrm/crm/service/SystemSettingService.java`
- `backend/src/main/java/com/aicrm/crm/service/InsightService.java`
- `backend/src/main/java/com/aicrm/crm/api/AdminSettingController.java`

**前端 — 修改：**
- `frontend/src/types.ts`
- `frontend/src/api.ts`
- `frontend/src/features/admin/AdminSettingsPage.tsx`

---

### Task 1: V17 DB Migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V17__add_ai_providers.sql`

- [ ] **Step 1: 建立 ai_providers 表 + 遷移 model_options 格式**

```sql
-- 建立 AI provider 設定表
CREATE TABLE ai_providers (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(64) NOT NULL UNIQUE,
    base_url    TEXT,
    api_key     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  VARCHAR(64)
);

-- 新增 current provider ID 設定鍵（空字串 = 未指定 provider）
INSERT INTO system_settings (setting_key, setting_value, updated_at, updated_by)
VALUES ('ai.chat.provider_id', '', now(), null)
ON CONFLICT (setting_key) DO NOTHING;

-- 將 model_options 從純字串陣列遷移為物件陣列（providerId: null 待用戶指定）
-- 只在現有值為有效 JSON 陣列時執行；空值或非陣列跳過
UPDATE system_settings
SET setting_value = (
    SELECT COALESCE(
        (
            SELECT jsonb_agg(
                jsonb_build_object('model', item.value, 'providerId', null)
            )::text
            FROM jsonb_array_elements_text(setting_value::jsonb) AS item(value)
        ),
        '[]'
    )
)
WHERE setting_key = 'ai.chat.model_options'
  AND setting_value IS NOT NULL
  AND setting_value ~ '^\s*\[';
```

---

### Task 2: AiProvider Entity + Repository

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/domain/AiProvider.java`
- Create: `backend/src/main/java/com/aicrm/crm/repository/AiProviderRepository.java`

- [ ] **Step 1: 建立 AiProvider Entity**

```java
package com.aicrm.crm.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * AI 供應商設定 Entity：儲存 provider 名稱、API endpoint 與金鑰。
 * apiKey 欄位僅供後端讀取，永不序列化回前端。
 */
@Entity
@Table(name = "ai_providers")
public class AiProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 供應商識別名稱（唯一），如 "OpenAI"、"Anthropic"。 */
    @Column(nullable = false, unique = true, length = 64)
    private String name;

    /** API base URL；null 或空字串代表使用 OpenAI 預設（https://api.openai.com）。 */
    @Column(name = "base_url")
    private String baseUrl;

    /** API 金鑰（敏感欄位，GET API 絕對不回傳原文）。 */
    @Column(name = "api_key")
    private String apiKey;

    /** 建立時間。 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 最後更新時間。 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 最後修改者帳號。 */
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    protected AiProvider() {}

    /** 新增 provider 建構子。 */
    public AiProvider(String name, String baseUrl, String apiKey, String updatedBy) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.updatedBy = updatedBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** 更新 provider 設定；apiKey 為 null 時保留現有金鑰不異動。 */
    public void update(String name, String baseUrl, String apiKey, String updatedBy) {
        this.name = name;
        this.baseUrl = baseUrl;
        if (apiKey != null) this.apiKey = apiKey;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getBaseUrl() { return baseUrl; }
    public String getApiKey() { return apiKey; }
    /** apiKey 是否已設定（非空）。 */
    public boolean isApiKeySet() { return apiKey != null && !apiKey.isBlank(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
```

- [ ] **Step 2: 建立 AiProviderRepository**

```java
package com.aicrm.crm.repository;

import com.aicrm.crm.domain.AiProvider;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AI 供應商設定資料存取。
 */
public interface AiProviderRepository extends JpaRepository<AiProvider, Long> {
    /** 檢查名稱是否已存在（新增時用）。 */
    boolean existsByName(String name);
    /** 檢查名稱是否已存在但排除自身（更新時用）。 */
    boolean existsByNameAndIdNot(String name, Long id);
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/domain/AiProvider.java \
        backend/src/main/java/com/aicrm/crm/repository/AiProviderRepository.java \
        backend/src/main/resources/db/migration/V17__add_ai_providers.sql
git commit -m "feat(backend): AiProvider entity, repository, V17 migration"
```

---

### Task 3: Dtos.java 修改 AI 相關 record

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/api/Dtos.java`（約 427–447 行）

- [ ] **Step 1: 找到現有 AI DTO 區塊並整體替換**

找到：
```java
    public record AiSettingsResponse(String currentModel, List<String> modelOptions,
            String envDefaultModel, String source) {}

    public record AiSettingsRequest(String model, List<String> modelOptions) {}

    public record AiTestRequest(String message, String model) {}

    public record ModelResultItem(
```

替換為（`ModelResultItem` 開頭的部分維持不動，僅替換上方四段）：

```java
    /** 模型設定項目（含供應商關聯）。 */
    public record ModelOptionItem(String model, Long providerId) {}

    /** AI 供應商檢視（apiKey 永不回傳前端，以 apiKeySet 布林代替）。 */
    public record AiProviderItem(Long id, String name, String baseUrl, boolean apiKeySet) {}

    /** 新增/更新供應商請求；apiKey 為 null 代表不更換現有金鑰。 */
    public record AiProviderRequest(
        @jakarta.validation.constraints.NotBlank String name,
        String baseUrl,
        String apiKey
    ) {}

    /** AI 設定回應（含供應商清單與帶 providerId 的模型選項）。 */
    public record AiSettingsResponse(
        String currentModel,
        Long currentProviderId,
        List<ModelOptionItem> modelOptions,
        List<AiProviderItem> providers,
        String envDefaultModel,
        String source
    ) {}

    /** AI 設定更新請求。 */
    public record AiSettingsRequest(String model, Long providerId, List<ModelOptionItem> modelOptions) {}

    /** 模型競速測試請求（providerId 指定使用哪組 API 憑證）。 */
    public record AiTestRequest(String message, String model, Long providerId) {}

    public record ModelResultItem(
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/api/Dtos.java
git commit -m "feat(backend): Dtos multi-provider AI records"
```

---

### Task 4: SystemSettingService 新增 Provider CRUD 與更新模型選項格式

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/service/SystemSettingService.java`

- [ ] **Step 1: 新增 import 與 AiProviderRepository 注入**

在現有 import 下方新增：
```java
import com.aicrm.crm.domain.AiProvider;
import com.aicrm.crm.repository.AiProviderRepository;
import java.util.Optional;
```

新增 class 常數：
```java
/** current provider ID 設定鍵。 */
public static final String KEY_AI_CHAT_PROVIDER_ID = "ai.chat.provider_id";

/** AI 供應商資料存取。 */
private final AiProviderRepository providerRepository;
```

更新建構子，加入 `AiProviderRepository providerRepository` 參數並賦值：
```java
public SystemSettingService(SystemSettingRepository repository,
                            AiProviderRepository providerRepository,
                            ObjectMapper objectMapper,
                            @Value("${spring.ai.openai.chat.model:}") String envDefaultModel) {
    this.repository = repository;
    this.providerRepository = providerRepository;
    this.objectMapper = objectMapper;
    this.envDefaultModel = envDefaultModel;
}
```

- [ ] **Step 2: 替換 getModelOptions 為回傳 ModelOptionItem 清單**

刪除舊的 `getModelOptions()`，改為：
```java
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
```

- [ ] **Step 3: 新增 getCurrentProviderId + getProviders + findProviderById**

在 `resolveChatOptions()` 之後新增：

```java
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
 * 更新 provider；apiKey 為 null 時保留現有金鑰；name 重複（排除自身）回例外。
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
```

- [ ] **Step 4: 替換 getAiSettingsView 與 updateAiSettings**

替換 `getAiSettingsView()`：
```java
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
```

替換 `updateAiSettings()`：
```java
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
```

- [ ] **Step 5: 新增 parseModelOptions / serializeModelOptions，刪除舊的 parseOptions / serializeOptions**

```java
/** 解析 ModelOptionItem 清單 JSON；任何錯誤回空清單並記 log。 */
private List<Dtos.ModelOptionItem> parseModelOptions(String json) {
    try {
        if (!StringUtils.hasText(json)) return List.of();
        return objectMapper.readValue(json, new TypeReference<List<Dtos.ModelOptionItem>>() {});
    } catch (RuntimeException e) {
        log.warn("模型清單 JSON 解析失敗，回空清單：{}", e.getMessage());
        return List.of();
    }
}

/** 序列化 ModelOptionItem 清單為 JSON 字串。 */
private String serializeModelOptions(List<Dtos.ModelOptionItem> options) {
    return objectMapper.writeValueAsString(options);
}
```

刪除舊的 `parseOptions(String json)` 與 `serializeOptions(List<String> options)` 方法。

- [ ] **Step 6: 編譯確認**

```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend compile -q
```
Expected: BUILD SUCCESS（若有 `resolveChatOptions()` 因 `getAiChatModel()` 回傳 `Optional<String>` 而型別不符，保持不變；若需修改僅更新其 return 型別為 `Optional<String>` 的 consumer）

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/SystemSettingService.java
git commit -m "feat(backend): SystemSettingService provider CRUD, ModelOptionItem format"
```

---

### Task 5: InsightService — streamModelTest 支援動態 Provider

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/service/InsightService.java`

- [ ] **Step 1: 新增 import**

```java
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.chat.model.ChatModel;
```

- [ ] **Step 2: 新增 buildChatModelForProvider 私有方法（在 class 末尾私有方法區）**

```java
/**
 * 以指定 provider 的 URL 與 apiKey 動態建立 OpenAI 相容的 ChatModel。
 * 函式級註解：不快取—每次競速測試建立一次，用完由 GC 回收。
 *
 * @param providerId provider DB id
 * @return ChatModel；provider 不存在或 apiKey 未設定時回 null
 */
private ChatModel buildChatModelForProvider(Long providerId) {
    if (providerId == null) return null;
    var provider = systemSettings.findProviderById(providerId).orElse(null);
    if (provider == null || !provider.isApiKeySet()) return null;
    var resolvedBaseUrl = (provider.getBaseUrl() != null && !provider.getBaseUrl().isBlank())
            ? provider.getBaseUrl()
            : "https://api.openai.com";
    var openAiApi = OpenAiApi.builder()
            .apiKey(provider.getApiKey())
            .baseUrl(resolvedBaseUrl)
            .build();
    return OpenAiChatModel.builder()
            .openAiApi(openAiApi)
            .defaultOptions(OpenAiChatOptions.builder().temperature(0.3f).build())
            .build();
}
```

- [ ] **Step 3: 修改 streamModelTest 方法簽名，加入 providerId 參數**

原簽名：`public SseEmitter streamModelTest(String model, String ignored)`

新簽名：`public SseEmitter streamModelTest(String model, Long providerId, String ignored)`

修改方法開頭的 ChatModel 解析（第 791 行附近），將：
```java
var chatModel = aiEnabled ? chatModelProvider.getIfAvailable() : null;
if (chatModel == null) {
    sendContent(emitter, "⚠️ 未設定 API 金鑰，無法執行模型測試。");
    sendSimpleTailAndComplete(emitter, null);
    return emitter;
}
```

替換為：
```java
// 優先使用 provider 動態憑證；未指定 provider 時回退 Spring 注入的預設 ChatModel
final ChatModel chatModel;
if (providerId != null) {
    chatModel = buildChatModelForProvider(providerId);
    if (chatModel == null) {
        sendContent(emitter, "⚠️ Provider 不存在或 API 金鑰未設定，無法執行模型測試。");
        sendSimpleTailAndComplete(emitter, null);
        return emitter;
    }
} else {
    var defaultModel = aiEnabled ? chatModelProvider.getIfAvailable() : null;
    if (defaultModel == null) {
        sendContent(emitter, "⚠️ 未設定 API 金鑰，無法執行模型測試。");
        sendSimpleTailAndComplete(emitter, null);
        return emitter;
    }
    chatModel = defaultModel;
}
```

方法其餘邏輯（grounding context、nonce、stream subscribe）完全不動。

- [ ] **Step 4: 編譯確認**

```powershell
mvn -pl backend compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/InsightService.java
git commit -m "feat(backend): streamModelTest dynamic ChatModel per provider"
```

---

### Task 6: AdminSettingController Provider CRUD 端點

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/api/AdminSettingController.java`

- [ ] **Step 1: 新增 import**

```java
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
```

- [ ] **Step 2: 更新 updateAiSettings 呼叫（AiSettingsRequest 欄位異動）**

```java
@PutMapping("/ai")
public Dtos.AiSettingsResponse updateAiSettings(@RequestBody Dtos.AiSettingsRequest request,
                                                 Authentication authentication) {
    try {
        systemSettings.updateAiSettings(
            request.model(), request.providerId(), request.modelOptions(),
            resolveUsername(authentication));
    } catch (IllegalArgumentException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
    return systemSettings.getAiSettingsView();
}
```

- [ ] **Step 3: 更新 testModel 呼叫（AiTestRequest 新增 providerId）**

```java
@PostMapping(value = "/ai/test", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter testModel(@RequestBody Dtos.AiTestRequest request, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
    response.setHeader("Pragma", "no-cache");
    response.setHeader("X-Accel-Buffering", "no");
    return insightService.streamModelTest(request.model(), request.providerId(), request.message());
}
```

- [ ] **Step 4: 新增 Provider CRUD 端點（加在 scoreCalls 方法之前）**

```java
/**
 * 取得所有 AI 供應商（不含 apiKey）。
 *
 * @return provider 清單
 */
@GetMapping("/ai/providers")
public List<Dtos.AiProviderItem> getProviders() {
    return systemSettings.getProviders();
}

/**
 * 新增 AI 供應商。
 *
 * @param request 供應商請求 DTO
 * @param authentication 登入認證
 * @return 新增後的 provider
 */
@PostMapping("/ai/providers")
@org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
public Dtos.AiProviderItem createProvider(@Valid @RequestBody Dtos.AiProviderRequest request,
                                           Authentication authentication) {
    try {
        return systemSettings.createProvider(request, resolveUsername(authentication));
    } catch (IllegalArgumentException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}

/**
 * 更新 AI 供應商；apiKey 為 null 時保留現有金鑰。
 *
 * @param id provider id
 * @param request 供應商請求 DTO
 * @param authentication 登入認證
 * @return 更新後的 provider
 */
@PutMapping("/ai/providers/{id}")
public Dtos.AiProviderItem updateProvider(@PathVariable Long id,
                                           @Valid @RequestBody Dtos.AiProviderRequest request,
                                           Authentication authentication) {
    try {
        return systemSettings.updateProvider(id, request, resolveUsername(authentication));
    } catch (IllegalArgumentException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}

/**
 * 刪除 AI 供應商。
 *
 * @param id provider id
 */
@DeleteMapping("/ai/providers/{id}")
@org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
public void deleteProvider(@PathVariable Long id) {
    try {
        systemSettings.deleteProvider(id);
    } catch (IllegalArgumentException e) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
```

- [ ] **Step 5: 後端 compile + 啟動冒煙測試**

```powershell
mvn -pl backend compile -q
# 啟動後端（需 DB 連線）
mvn -pl backend spring-boot:run
# 另開 terminal，取得 TOKEN 後測試
Invoke-RestMethod -Uri "http://localhost:18080/api/admin/settings/ai/providers" `
  -Headers @{Authorization="Bearer $TOKEN"}
```
Expected: 回傳空陣列 `[]`（首次啟動，DB 無 provider）

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/api/AdminSettingController.java
git commit -m "feat(backend): provider CRUD endpoints, updated AI settings controller"
```

---

### Task 7: 前端 types.ts + api.ts

**Files:**
- Modify: `frontend/src/types.ts`
- Modify: `frontend/src/api.ts`

- [ ] **Step 1: 修改 types.ts — 找到 AiSettingsResponse 並替換**

找到現有 `AiSettingsResponse`（含 `ModelResultItem` 等），在其前方插入新 type，並替換舊的 `AiSettingsResponse`：

```typescript
/** 模型設定項目（含供應商關聯）。 */
export interface ModelOptionItem {
  model: string;
  providerId: number | null;
}

/** AI 供應商資訊（前端不顯示 apiKey 原文）。 */
export interface AiProviderItem {
  id: number;
  name: string;
  baseUrl: string | null;
  apiKeySet: boolean;
}

/** AI 設定回應（含 providers 清單與帶 providerId 的 modelOptions）。 */
export interface AiSettingsResponse {
  currentModel: string;
  currentProviderId: number | null;
  modelOptions: ModelOptionItem[];
  providers: AiProviderItem[];
  envDefaultModel: string;
  source: string;
}
```

（舊的 `export interface AiSettingsResponse` 只有 `currentModel`、`modelOptions: string[]`、`envDefaultModel`、`source` 的定義必須刪除）

- [ ] **Step 2: 修改 api.ts — saveAiSettings 新簽名**

找到現有 `saveAiSettings` 並替換：

```typescript
/** 更新 AI 設定（限 ADMIN），回傳更新後的設定。 */
export async function saveAiSettings(
  model: string,
  providerId: number | null,
  modelOptions: import("./types").ModelOptionItem[]
) {
  const { data } = await apiClient.put<import("./types").AiSettingsResponse>(
    "/admin/settings/ai",
    { model, providerId, modelOptions }
  );
  return data;
}
```

- [ ] **Step 3: 修改 api.ts — streamModelTest 加入 providerId**

找到現有 `streamModelTest` function，在 `body` 組裝處加入 `providerId`：

```typescript
export async function streamModelTest(
  message: string,
  model: string,
  providerId: number | null,
  onChunk: (chunk: SseChunk) => void,
  onDone: () => void,
  onError: (err: any) => void
) {
  // 函式內 body 改為：
  const body = JSON.stringify({ message, model, providerId });
  // 其餘 fetch + SSE 邏輯完全不動
```

- [ ] **Step 4: 新增 Provider CRUD API 函式（在 saveAiSettings 下方）**

```typescript
/** 新增 provider（限 ADMIN）。 */
export async function createAiProvider(name: string, baseUrl: string, apiKey: string) {
  const { data } = await apiClient.post<import("./types").AiProviderItem>(
    "/admin/settings/ai/providers",
    { name, baseUrl, apiKey }
  );
  return data;
}

/** 更新 provider；apiKey 為 null 代表保留現有金鑰。 */
export async function updateAiProvider(
  id: number,
  name: string,
  baseUrl: string,
  apiKey: string | null
) {
  const { data } = await apiClient.put<import("./types").AiProviderItem>(
    `/admin/settings/ai/providers/${id}`,
    { name, baseUrl, apiKey }
  );
  return data;
}

/** 刪除 provider（限 ADMIN）。 */
export async function deleteAiProvider(id: number) {
  await apiClient.delete(`/admin/settings/ai/providers/${id}`);
}
```

- [ ] **Step 5: TypeScript 型別檢查**

```powershell
cd d:/GitHub/ai-crm/frontend
pnpm exec tsc --noEmit
```
Expected: exit 0

- [ ] **Step 6: Commit**

```bash
git add frontend/src/types.ts frontend/src/api.ts
git commit -m "feat(frontend): types and API for multi-provider AI settings"
```

---

### Task 8: AdminSettingsPage — Provider 管理卡片 UI

**Files:**
- Modify: `frontend/src/features/admin/AdminSettingsPage.tsx`

- [ ] **Step 1: 更新 import**

```typescript
import {
  fetchAiSettings, saveAiSettings, streamModelTest,
  streamModelScore, fetchModelScoreCalls,
  createAiProvider, updateAiProvider, deleteAiProvider,
} from "../../api";
import type {
  AiSettingsResponse, AiCallHistoryItem, ModelResultItem,
  AiProviderItem, ModelOptionItem,
} from "../../types";
```

- [ ] **Step 2: 新增 Provider 相關 state（在「設定區狀態」之後）**

```typescript
/* ── Provider 管理狀態 ─────────────────────────────── */
const [providers, setProviders] = useState<AiProviderItem[]>([]);
const [providerForm, setProviderForm] = useState({ name: "", baseUrl: "", apiKey: "" });
const [editingProviderId, setEditingProviderId] = useState<number | null>(null);
const [providerError, setProviderError] = useState<string | null>(null);
const [savingProvider, setSavingProvider] = useState(false);
```

同時將 `options` state 型別改為 `ModelOptionItem[]`，並新增 `currentProviderId` state：
```typescript
const [options, setOptions] = useState<ModelOptionItem[]>([]);
const [currentProviderId, setCurrentProviderId] = useState<number | null>(null);
const [newModelProviderId, setNewModelProviderId] = useState<number | null>(null);
```

- [ ] **Step 3: 更新 load() 解析 providers、currentProviderId、modelOptions**

```typescript
async function load() {
  setLoading(true);
  setSettingError(null);
  try {
    const data = await fetchAiSettings();
    setSettings(data);
    setCurrentModel(data.currentModel);
    setCurrentProviderId(data.currentProviderId);
    setOptions(data.modelOptions);
    setProviders(data.providers);
  } catch (e) {
    setSettingError(e instanceof Error ? e.message : "載入失敗");
  } finally {
    setLoading(false);
  }
}
```

- [ ] **Step 4: 更新 save() 呼叫新 saveAiSettings 簽名**

```typescript
async function save() {
  setSaving(true);
  setSettingError(null);
  setActionMsg(null);
  try {
    const data = await saveAiSettings(currentModel, currentProviderId, options);
    setSettings(data);
    setCurrentModel(data.currentModel);
    setCurrentProviderId(data.currentProviderId);
    setOptions(data.modelOptions);
    setProviders(data.providers);
    setActionMsg("已儲存，AI 呼叫即時生效。");
  } catch (e) {
    setSettingError(e instanceof Error ? e.message : "儲存失敗");
  } finally {
    setSaving(false);
  }
}
```

- [ ] **Step 5: 更新 selectModel / addModel / removeModel**

```typescript
function selectModel(m: string, pid: number | null) {
  if (currentModel === m) { setCurrentModel(""); setCurrentProviderId(null); }
  else { setCurrentModel(m); setCurrentProviderId(pid ?? null); }
  setActionMsg(null);
}

function addModel() {
  const m = newModel.trim();
  if (!m) return;
  if (!options.find(o => o.model === m)) {
    setOptions(prev => [...prev, { model: m, providerId: newModelProviderId }]);
  }
  setCurrentModel(m);
  setCurrentProviderId(newModelProviderId);
  setNewModel("");
  setNewModelProviderId(null);
  setActionMsg(null);
}

function removeModel(m: string) {
  setOptions(prev => prev.filter(o => o.model !== m));
  if (currentModel === m) { setCurrentModel(""); setCurrentProviderId(null); }
  setActionMsg(null);
}
```

- [ ] **Step 6: 新增 Provider CRUD 方法**

```typescript
async function saveProviderForm() {
  setSavingProvider(true);
  setProviderError(null);
  try {
    if (editingProviderId !== null) {
      const apiKey = providerForm.apiKey.trim() || null; // 空字串 = 保留現有
      const updated = await updateAiProvider(editingProviderId, providerForm.name, providerForm.baseUrl, apiKey);
      setProviders(prev => prev.map(p => p.id === editingProviderId ? updated : p));
    } else {
      const created = await createAiProvider(providerForm.name, providerForm.baseUrl, providerForm.apiKey);
      setProviders(prev => [...prev, created]);
    }
    setProviderForm({ name: "", baseUrl: "", apiKey: "" });
    setEditingProviderId(null);
  } catch (e) {
    setProviderError(e instanceof Error ? e.message : "儲存失敗");
  } finally {
    setSavingProvider(false);
  }
}

function startEditProvider(p: AiProviderItem) {
  setEditingProviderId(p.id);
  setProviderForm({ name: p.name, baseUrl: p.baseUrl ?? "", apiKey: "" }); // apiKey 絕不預填
  setProviderError(null);
}

async function handleDeleteProvider(id: number) {
  if (!window.confirm("確定刪除此供應商？相關模型選項的 provider 關聯將清除為 null。")) return;
  try {
    await deleteAiProvider(id);
    setProviders(prev => prev.filter(p => p.id !== id));
    setOptions(prev => prev.map(o => o.providerId === id ? { ...o, providerId: null } : o));
    if (currentProviderId === id) setCurrentProviderId(null);
  } catch (e) {
    setProviderError(e instanceof Error ? e.message : "刪除失敗");
  }
}
```

- [ ] **Step 7: 在 JSX 中插入 Provider 管理卡片（在「AI 對話模型」`<div className="panel">` 之前）**

```tsx
{/* ── Provider 管理卡片 ── */}
<div className="panel" style={{ marginBottom: 16 }}>
  <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14 }}>
    <span style={{ fontSize: 16, fontWeight: 700, color: "#122232" }}>🔑 AI 供應商</span>
    <span style={{ fontSize: 12, color: "#94a3b8" }}>管理 API 金鑰與 Base URL，模型選項關聯至供應商</span>
  </div>

  {providerError && (
    <div style={{ background: "#fef2f2", border: "1px solid #fca5a5", borderRadius: 8,
      padding: "8px 12px", marginBottom: 10, color: "#b91c1c", fontSize: 13 }}>
      ⚠️ {providerError}
    </div>
  )}

  <div style={{ display: "flex", flexDirection: "column", gap: 6, marginBottom: 12 }}>
    {providers.length === 0 && (
      <p style={{ fontSize: 13, color: "#94a3b8", margin: 0 }}>尚無供應商，請在下方新增。</p>
    )}
    {providers.map(p => (
      <div key={p.id} style={{
        display: "flex", alignItems: "center", justifyContent: "space-between",
        padding: "10px 14px", background: "#f8fafc",
        border: `1.5px solid ${editingProviderId === p.id ? "#6366f1" : "#e2e8f0"}`,
        borderRadius: 8,
      }}>
        <div>
          <span style={{ fontWeight: 600, fontSize: 14, color: "#122232", marginRight: 8 }}>{p.name}</span>
          <span style={{ fontSize: 12, color: "#64748b" }}>{p.baseUrl || "預設 OpenAI URL"}</span>
          <span style={{
            marginLeft: 8, fontSize: 11, padding: "1px 6px", borderRadius: 4,
            color: p.apiKeySet ? "#166534" : "#b91c1c",
            background: p.apiKeySet ? "#dcfce7" : "#fee2e2",
          }}>
            {p.apiKeySet ? "🔐 金鑰已設定" : "⚠️ 未設定金鑰"}
          </span>
        </div>
        <div style={{ display: "flex", gap: 6 }}>
          <button type="button" className="btn-secondary"
            style={{ fontSize: 12, padding: "3px 10px" }}
            onClick={() => startEditProvider(p)}>編輯</button>
          <button type="button" className="btn-danger"
            style={{ fontSize: 12, padding: "3px 10px" }}
            onClick={() => handleDeleteProvider(p.id)}>刪除</button>
        </div>
      </div>
    ))}
  </div>

  {/* 新增 / 編輯表單 */}
  <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: "12px 14px" }}>
    <div style={{ fontSize: 13, fontWeight: 600, color: "#475569", marginBottom: 10 }}>
      {editingProviderId !== null ? "✏️ 編輯供應商" : "➕ 新增供應商"}
    </div>
    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
      <input
        value={providerForm.name}
        placeholder="供應商名稱，如 OpenAI、Anthropic"
        onChange={e => setProviderForm(prev => ({ ...prev, name: e.target.value }))}
        style={{ padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, outline: "none" }}
      />
      <input
        value={providerForm.baseUrl}
        placeholder="Base URL（留空使用 OpenAI 預設：https://api.openai.com）"
        onChange={e => setProviderForm(prev => ({ ...prev, baseUrl: e.target.value }))}
        style={{ padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, outline: "none" }}
      />
      <input
        type="password"
        value={providerForm.apiKey}
        placeholder={editingProviderId !== null ? "API Key（留空保留現有金鑰）" : "API Key"}
        onChange={e => setProviderForm(prev => ({ ...prev, apiKey: e.target.value }))}
        style={{ padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, outline: "none" }}
      />
      <div style={{ display: "flex", gap: 8 }}>
        <button type="button" className="btn-primary"
          disabled={savingProvider || !providerForm.name.trim()}
          onClick={saveProviderForm}
          style={{ flex: 1, padding: "8px", fontWeight: 700 }}>
          {savingProvider ? "儲存中…" : editingProviderId !== null ? "更新供應商" : "新增供應商"}
        </button>
        {editingProviderId !== null && (
          <button type="button" className="btn-secondary"
            onClick={() => {
              setEditingProviderId(null);
              setProviderForm({ name: "", baseUrl: "", apiKey: "" });
              setProviderError(null);
            }}
            style={{ padding: "8px 16px" }}>取消</button>
        )}
      </div>
    </div>
  </div>
</div>
```

- [ ] **Step 8: TypeScript 型別檢查**

```powershell
pnpm exec tsc --noEmit
```
Expected: exit 0

- [ ] **Step 9: Commit**

```bash
git add frontend/src/features/admin/AdminSettingsPage.tsx
git commit -m "feat(frontend): provider management UI"
```

---

### Task 9: AdminSettingsPage — 模型清單 + 競速測試整合 Provider

**Files:**
- Modify: `frontend/src/features/admin/AdminSettingsPage.tsx`

- [ ] **Step 1: 更新模型清單 JSX（顯示 provider badge，更新 onClick 傳 providerId）**

找到 `{options.map((o) => {`（原本是 `options.map((o) =>`），整個 map 改為：

```tsx
{options.map((o) => {
  const isSelected = currentModel === o.model;
  const providerName = providers.find(p => p.id === o.providerId)?.name;
  return (
    <div
      key={`${o.model}-${o.providerId ?? "none"}`}
      onClick={() => selectModel(o.model, o.providerId ?? null)}
      style={{
        display: "flex", alignItems: "center", justifyContent: "space-between",
        padding: "10px 14px",
        background: isSelected ? "#f0fdf4" : "#f8fafc",
        border: `1.5px solid ${isSelected ? "#4ade80" : "#e2e8f0"}`,
        borderRadius: 8, cursor: "pointer",
        transition: "border-color 0.15s, background 0.15s",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
        <div style={{
          width: 16, height: 16, borderRadius: "50%",
          border: `2px solid ${isSelected ? "#16a34a" : "#cbd5e1"}`,
          background: isSelected ? "#16a34a" : "transparent",
          flexShrink: 0, display: "flex", alignItems: "center", justifyContent: "center",
        }}>
          {isSelected && <div style={{ width: 6, height: 6, borderRadius: "50%", background: "#fff" }} />}
        </div>
        <span style={{ fontFamily: "monospace", fontSize: 14, color: "#122232" }}>{o.model}</span>
        {providerName && (
          <span style={{ fontSize: 11, color: "#6366f1", background: "#ede9fe",
            padding: "1px 6px", borderRadius: 4 }}>
            {providerName}
          </span>
        )}
        {isSelected && (
          <span style={{ fontSize: 11, color: "#166534", background: "#dcfce7",
            padding: "1px 6px", borderRadius: 4, fontWeight: 600 }}>使用中</span>
        )}
      </div>
      <button
        type="button"
        className="btn-danger"
        style={{ padding: "3px 10px", fontSize: 12 }}
        onClick={(e) => { e.stopPropagation(); removeModel(o.model); }}
      >
        刪除
      </button>
    </div>
  );
})}
```

- [ ] **Step 2: 更新「新增模型」輸入列，加入 Provider 選擇下拉**

找到現有的新增輸入列 `<div style={{ display: "flex", gap: 8, paddingTop: 12, borderTop: ...`，整段替換為：

```tsx
<div style={{ display: "flex", gap: 8, paddingTop: 12, borderTop: "1px solid #f1f5f9", marginBottom: 16, flexWrap: "wrap" }}>
  <select
    value={newModelProviderId ?? ""}
    onChange={e => setNewModelProviderId(e.target.value ? Number(e.target.value) : null)}
    style={{ padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, outline: "none", minWidth: 140 }}
  >
    <option value="">選擇供應商</option>
    {providers.map(p => (
      <option key={p.id} value={p.id}>{p.name}</option>
    ))}
  </select>
  <input
    value={newModel}
    placeholder="輸入模型名，如 claude-sonnet-4-6"
    onChange={(e) => setNewModel(e.target.value)}
    onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addModel(); } }}
    style={{ flex: 1, minWidth: 200, padding: "8px 12px", border: "1px solid #d1e0db", borderRadius: 8, fontSize: 14, outline: "none" }}
  />
  <button type="button" className="btn-secondary" style={{ whiteSpace: "nowrap", padding: "8px 16px" }} onClick={addModel}>
    + 新增
  </button>
</div>
```

- [ ] **Step 3: 更新 startRace 競速迴圈，用 ModelOptionItem 的 providerId**

找到 `options.forEach((model) => {`，改為：

```typescript
options.forEach((opt) => {
  const t0 = performance.now();
  startTimeRef.current[opt.model] = t0;

  streamModelTest(
    "", opt.model, opt.providerId ?? null,
    (chunk: any) => {
      if (chunk.type === "content" && chunk.delta) {
        const now = performance.now();
        setRaceResults((prev) => {
          const cur = prev[opt.model] ?? { status: "streaming", content: "", firstTokenMs: null, totalMs: null,
                                           promptTokens: null, completionTokens: null, totalTokens: null };
          return {
            ...prev,
            [opt.model]: {
              ...cur,
              status: "streaming",
              content: cur.content + chunk.delta,
              firstTokenMs: cur.firstTokenMs === null ? Math.round(now - t0) : cur.firstTokenMs,
            }
          };
        });
      } else if (chunk.type === "tokens") {
        setRaceResults((prev) => ({
          ...prev,
          [opt.model]: {
            ...prev[opt.model],
            promptTokens: chunk.promptTokens ?? null,
            completionTokens: chunk.completionTokens ?? null,
            totalTokens: chunk.totalTokens ?? null,
          }
        }));
      }
    },
    () => {
      const totalMs = Math.round(performance.now() - (startTimeRef.current[opt.model] ?? 0));
      setRaceResults((prev) => ({
        ...prev,
        [opt.model]: { ...prev[opt.model], status: "done", totalMs }
      }));
      doneCount++;
      if (doneCount >= total) setRacing(false);
    },
    (err) => {
      setRaceResults((prev) => ({
        ...prev,
        [opt.model]: { ...prev[opt.model], status: "error", errorMsg: err?.message ?? "連線失敗" }
      }));
      doneCount++;
      if (doneCount >= total) setRacing(false);
    }
  );
});
```

其他部分（init 的 `options.forEach((m)`）也改為 `options.forEach((opt)` 並用 `opt.model` 作 key。

- [ ] **Step 4: 在競速結果卡片 model 標頭旁顯示 provider badge**

在 `{options.map((model) => {` 改為 `{options.map((opt) => {`，並取得 provider name：

```tsx
{options.map((opt) => {
  const r = raceResults[opt.model] ?? { status: "idle" as const, content: "", ... };
  const providerName = providers.find(p => p.id === opt.providerId)?.name;
  // ...
  return (
    <div key={`${opt.model}-${opt.providerId ?? "none"}`} ...>
      <div ...>
        {/* model 標頭 */}
        <div ...>
          <div ...>
            <span style={{ fontFamily: "monospace", fontSize: 13, fontWeight: 600, color: "#122232" }}>
              {opt.model}
            </span>
            {providerName && (
              <span style={{ fontSize: 10, color: "#6366f1", background: "#ede9fe",
                padding: "1px 5px", borderRadius: 3, marginLeft: 4 }}>
                {providerName}
              </span>
            )}
            <span style={{ fontSize: 11, color: statusColor, fontWeight: 600 }}>● {statusLabel}</span>
          </div>
          ...
        </div>
        ...
        {r.status === "done" && r.content && (
          <div style={{ padding: "6px 12px", borderTop: "1px solid #f1f5f9", textAlign: "right" }}>
            <button
              type="button"
              className="btn-secondary"
              style={{ fontSize: 12, padding: "3px 10px" }}
              onClick={() => downloadMarkdown(`${opt.model}-回答`, r.content)}
            >
              ⬇ 下載 MD
            </button>
          </div>
        )}
      </div>
    </div>
  );
})}
```

- [ ] **Step 5: TypeScript 型別檢查**

```powershell
pnpm exec tsc --noEmit
```
Expected: exit 0

- [ ] **Step 6: Commit**

```bash
git add frontend/src/features/admin/AdminSettingsPage.tsx
git commit -m "feat(frontend): model-provider binding, race test with per-provider credentials"
```

---

## Self-Review

**Spec coverage:**
- ✓ Provider 有 name / baseUrl / apiKey，均存 DB（Task 1–2）
- ✓ apiKey 永不回傳前端，以 `apiKeySet: boolean` 代替；input type="password"（Task 2, 8）
- ✓ 更新時 apiKey 空字串 = 保留現有金鑰（Task 4, 8 Step 6）
- ✓ 模型新增需選擇 provider（Task 9 Step 2）
- ✓ 競速測試用各模型的 provider 憑證動態建立 ChatModel（Task 5）
- ✓ 模型清單與競速卡片顯示 provider badge（Task 9 Step 1, 4）
- ✓ Provider 刪除後，關聯模型 providerId 清為 null（Task 8 Step 6）

**Placeholder scan:** 無 TBD / TODO / "similar to"。

**Type consistency:**
- `ModelOptionItem` — Dtos.java（Task 3）、SystemSettingService（Task 4）、types.ts（Task 7）三處欄位 `model`/`providerId` 一致
- `AiProviderItem` — Dtos.java（Task 3）、api.ts（Task 7）、UI state（Task 8）一致
- `streamModelTest(model, providerId, ignored)` — InsightService（Task 5）、AdminSettingController（Task 6）、api.ts（Task 7）、AdminSettingsPage（Task 9）簽名一致
- `updateAiSettings(model, providerId, modelOptions, username)` — SystemSettingService（Task 4）、AdminSettingController（Task 6）一致
