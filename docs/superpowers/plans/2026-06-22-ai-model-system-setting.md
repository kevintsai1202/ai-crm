# AI 模型系統設定 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ADMIN 可在 `/admin/settings` 設定 AI 對話模型（下拉選擇 + 自訂模型清單）；AI 呼叫優先讀 DB 設定的 model，未設定時回退環境變數預設。base-url / api-key 維持固定。

**Architecture:** 新增通用 key-value 設定表 `system_settings`，存 `ai.chat.model`（目前模型，空字串代表用環境變數）與 `ai.chat.model_options`（可選模型清單 JSON）。`SystemSettingService` 提供 `resolveChatOptions()`：有設定模型時回 `OpenAiChatOptions`，否則回 null。四個 LLM 呼叫點在 `ChatClient` fluent 鏈上條件式加 `.options(...)`，null 時不覆蓋 → 沿用 Spring AI 由環境變數初始化的 ChatModel bean（base-url / api-key / 預設 model 不動）。前端新增 ADMIN-only 設定頁。

**Tech Stack:** Java 21、Spring Boot 4.1（Jackson 3 `tools.jackson`）、Spring AI 2.0、Flyway、JPA/PostgreSQL；React + TypeScript + axios。

**驗證環境前置（每次跑後端測試先設定）：**
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

---

## File Structure

**後端（新建）**
- `backend/src/main/resources/db/migration/V16__add_system_settings.sql` — 建表 + 種子兩列（最新業務 migration 為 V15，故新檔為 V16）
- `backend/src/main/java/com/aicrm/crm/domain/SystemSetting.java` — Entity（key-value）
- `backend/src/main/java/com/aicrm/crm/repository/SystemSettingRepository.java` — JpaRepository
- `backend/src/main/java/com/aicrm/crm/service/SystemSettingService.java` — 讀寫 + resolveChatOptions
- `backend/src/main/java/com/aicrm/crm/api/AdminSettingController.java` — GET/PUT `/api/admin/settings/ai`
- `backend/src/test/java/com/aicrm/crm/service/SystemSettingServiceTest.java` — Service 單元測試（Mockito）
- `backend/src/test/java/com/aicrm/crm/api/AdminSettingControllerTest.java` — Controller 權限/流程測試

**後端（修改）**
- `backend/src/main/java/com/aicrm/crm/api/Dtos.java` — 新增 `AiSettingsResponse` / `AiSettingsRequest` record
- `backend/src/main/java/com/aicrm/crm/service/InsightService.java` — 注入 SystemSettingService，2 個呼叫點加 options
- `backend/src/main/java/com/aicrm/crm/service/ManagerInsightService.java` — 注入 + 1 個呼叫點加 options
- `backend/src/main/java/com/aicrm/crm/service/SentimentIntentService.java` — 注入 + 1 個呼叫點加 options

**前端（新建）**
- `frontend/src/features/admin/AdminSettingsPage.tsx` — 設定頁

**前端（修改）**
- `frontend/src/types.ts` — 新增 `AiSettingsResponse` 介面
- `frontend/src/api.ts` — 新增 `fetchAiSettings()` / `saveAiSettings()`
- `frontend/src/App.tsx` — 新增 `/admin/settings` route
- `frontend/src/components/AppShell.tsx` — `NAV_ITEMS` 加「系統設定」

**SecurityConfig 不需改**：既有 `.requestMatchers("/api/admin/**").hasRole("ADMIN")` 已涵蓋。

---

## Task 1: 資料表與 Entity / Repository

**Files:**
- Create: `backend/src/main/resources/db/migration/V16__add_system_settings.sql`
- Create: `backend/src/main/java/com/aicrm/crm/domain/SystemSetting.java`
- Create: `backend/src/main/java/com/aicrm/crm/repository/SystemSettingRepository.java`

- [ ] **Step 1: 建立 Flyway migration**

建立 `backend/src/main/resources/db/migration/V16__add_system_settings.sql`（最新業務 migration 為 V15，故新檔為 V16）：

```sql
-- 通用系統設定（全域 key-value）。本次用於 AI 對話模型設定：
--   ai.chat.model          目前選用模型名；空字串代表「使用環境變數預設」
--   ai.chat.model_options  可選模型清單（JSON 陣列字串），供前端下拉
create table system_settings (
    setting_key   varchar(64) primary key,
    setting_value text not null,
    updated_at    timestamp with time zone not null,
    updated_by    varchar(64)
);

-- 種子：目前模型留空（走環境變數），預設候選清單兩筆
insert into system_settings (setting_key, setting_value, updated_at, updated_by) values
    ('ai.chat.model', '', now(), null),
    ('ai.chat.model_options', '["gemini-3.1-flash-lite-preview","gpt-4o-mini"]', now(), null);
```

- [ ] **Step 2: 建立 Entity**

建立 `backend/src/main/java/com/aicrm/crm/domain/SystemSetting.java`：

```java
package com.aicrm.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 系統設定（全域 key-value）：對應 system_settings 表。
 * 函式級註解：以 setting_key 為主鍵，setting_value 存純字串或 JSON 字串；自行管理 updated_at / updated_by。
 */
@Entity
@Table(name = "system_settings")
public class SystemSetting {

    /** 設定鍵（主鍵），如 ai.chat.model。 */
    @Id
    @Column(name = "setting_key", length = 64)
    private String settingKey;

    /** 設定值（純字串或 JSON 字串）。 */
    @Column(name = "setting_value", nullable = false, columnDefinition = "text")
    private String settingValue;

    /** 最後更新時間（自行管理）。 */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 最後修改者帳號（可為 null）。 */
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    protected SystemSetting() {
    }

    /**
     * 建立系統設定。
     *
     * @param settingKey 設定鍵
     * @param settingValue 設定值
     * @param updatedBy 修改者帳號
     */
    public SystemSetting(String settingKey, String settingValue, String updatedBy) {
        this.settingKey = settingKey;
        this.settingValue = settingValue;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    /** 更新設定值與修改者並刷新更新時間。 */
    public void updateValue(String settingValue, String updatedBy) {
        this.settingValue = settingValue;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public String getSettingKey() { return settingKey; }
    public String getSettingValue() { return settingValue; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
```

- [ ] **Step 3: 建立 Repository**

建立 `backend/src/main/java/com/aicrm/crm/repository/SystemSettingRepository.java`：

```java
package com.aicrm.crm.repository;

import com.aicrm.crm.domain.SystemSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 系統設定資料存取：以 setting_key 為主鍵查詢。
 */
public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {

    /**
     * 依設定鍵查詢。
     *
     * @param settingKey 設定鍵
     * @return 設定（可能不存在）
     */
    Optional<SystemSetting> findBySettingKey(String settingKey);
}
```

- [ ] **Step 4: 編譯驗證（含 Flyway migration 啟動驗證留待測試）**

Run:
```powershell
mvn -pl backend -q compile
```
Expected: BUILD SUCCESS（無編譯錯誤）。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V16__add_system_settings.sql backend/src/main/java/com/aicrm/crm/domain/SystemSetting.java backend/src/main/java/com/aicrm/crm/repository/SystemSettingRepository.java
git commit -m "feat(settings): system_settings 表 + Entity/Repository"
```

---

## Task 2: SystemSettingService（含 resolveChatOptions 回退邏輯）

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/service/SystemSettingService.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/SystemSettingServiceTest.java`

- [ ] **Step 1: 寫失敗測試**

建立 `backend/src/test/java/com/aicrm/crm/service/SystemSettingServiceTest.java`：

```java
package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aicrm.crm.domain.SystemSetting;
import com.aicrm.crm.repository.SystemSettingRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.databind.ObjectMapper;

/**
 * SystemSettingService 單元測試：以 Mockito mock repository，真 Jackson 3 ObjectMapper。
 * 驗證模型解析、回退（空字串走環境變數）、清單解析容錯、resolveChatOptions。
 */
class SystemSettingServiceTest {

    private final SystemSettingRepository repo = mock(SystemSettingRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    // envDefaultModel 模擬環境變數預設值
    private final SystemSettingService service = new SystemSettingService(repo, objectMapper, "gpt-4o-mini");

    @Test
    void getAiChatModelReturnsValueWhenSet() {
        when(repo.findBySettingKey("ai.chat.model"))
                .thenReturn(Optional.of(new SystemSetting("ai.chat.model", "gemini-3.1-flash-lite-preview", "admin")));
        assertThat(service.getAiChatModel()).contains("gemini-3.1-flash-lite-preview");
    }

    @Test
    void getAiChatModelEmptyWhenBlank() {
        when(repo.findBySettingKey("ai.chat.model"))
                .thenReturn(Optional.of(new SystemSetting("ai.chat.model", "", "admin")));
        assertThat(service.getAiChatModel()).isEmpty();
    }

    @Test
    void getAiChatModelEmptyWhenMissing() {
        when(repo.findBySettingKey("ai.chat.model")).thenReturn(Optional.empty());
        assertThat(service.getAiChatModel()).isEmpty();
    }

    @Test
    void getModelOptionsParsesJson() {
        when(repo.findBySettingKey("ai.chat.model_options"))
                .thenReturn(Optional.of(new SystemSetting("ai.chat.model_options", "[\"a\",\"b\"]", "admin")));
        assertThat(service.getModelOptions()).containsExactly("a", "b");
    }

    @Test
    void getModelOptionsReturnsEmptyOnBadJson() {
        when(repo.findBySettingKey("ai.chat.model_options"))
                .thenReturn(Optional.of(new SystemSetting("ai.chat.model_options", "not-json", "admin")));
        assertThat(service.getModelOptions()).isEmpty();
    }

    @Test
    void resolveChatOptionsNullWhenModelBlank() {
        when(repo.findBySettingKey("ai.chat.model"))
                .thenReturn(Optional.of(new SystemSetting("ai.chat.model", "", "admin")));
        assertThat(service.resolveChatOptions()).isNull();
    }

    @Test
    void resolveChatOptionsReturnsOpenAiOptionsWhenModelSet() {
        when(repo.findBySettingKey("ai.chat.model"))
                .thenReturn(Optional.of(new SystemSetting("ai.chat.model", "gemini-x", "admin")));
        var opts = service.resolveChatOptions();
        assertThat(opts).isInstanceOf(OpenAiChatOptions.class);
        assertThat(((OpenAiChatOptions) opts).getModel()).isEqualTo("gemini-x");
    }

    @Test
    void getAiSettingsViewReportsEnvSourceWhenBlank() {
        when(repo.findBySettingKey("ai.chat.model"))
                .thenReturn(Optional.of(new SystemSetting("ai.chat.model", "", "admin")));
        when(repo.findBySettingKey("ai.chat.model_options"))
                .thenReturn(Optional.of(new SystemSetting("ai.chat.model_options", "[\"gpt-4o-mini\"]", "admin")));
        var view = service.getAiSettingsView();
        assertThat(view.currentModel()).isEmpty();
        assertThat(view.envDefaultModel()).isEqualTo("gpt-4o-mini");
        assertThat(view.source()).isEqualTo("ENV");
        assertThat(view.modelOptions()).containsExactly("gpt-4o-mini");
    }

    @Test
    void updateAiSettingsRejectsModelNotInOptions() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.updateAiSettings("ghost-model", List.of("a", "b"), "admin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateAiSettingsUpsertsBothKeys() {
        when(repo.findBySettingKey(any())).thenReturn(Optional.empty());
        service.updateAiSettings("a", List.of("a", "b"), "admin");
        // 兩個 key 各 save 一次（model 與 options）
        org.mockito.Mockito.verify(repo, org.mockito.Mockito.times(2)).save(any(SystemSetting.class));
    }
}
```

注意：`AiSettingsResponse` record 在 Task 3 才加到 `Dtos.java`，但 `getAiSettingsView()` 回傳它。本測試與 Service 都引用它 → Task 2 與 Task 3 的 DTO 需一起編譯。**先在本 Task Step 2 把 DTO record 也加上**（見下），避免編譯失敗。

- [ ] **Step 2: 先加 DTO record（供 Service 回傳型別）**

在 `backend/src/main/java/com/aicrm/crm/api/Dtos.java` 既有 `DashboardLayoutRequest` record 之後（約第 318 行後），加入：

```java
    /** AI 設定回應：currentModel 空字串代表用環境變數；source 為 DB / ENV。 */
    public record AiSettingsResponse(String currentModel, List<String> modelOptions,
                                     String envDefaultModel, String source) {}

    /** AI 設定更新請求：model 為選用模型（空字串=用環境變數）；modelOptions 為候選清單。 */
    public record AiSettingsRequest(String model, List<String> modelOptions) {}
```
（`Dtos.java` 已 import `java.util.List`；若無則補上 `import java.util.List;`）

- [ ] **Step 3: 跑測試確認失敗**

Run:
```powershell
mvn -pl backend test -Dtest=SystemSettingServiceTest
```
Expected: 編譯失敗或測試失敗（`SystemSettingService` 尚未建立）。

- [ ] **Step 4: 實作 SystemSettingService**

建立 `backend/src/main/java/com/aicrm/crm/service/SystemSettingService.java`：

```java
package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.SystemSetting;
import com.aicrm.crm.repository.SystemSettingRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 系統設定服務：本次提供「AI 對話模型」設定的讀寫與回退解析。
 * 函式級註解：model 設定優先於環境變數；currentModel 為空字串時 resolveChatOptions 回 null，
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
    /** Jackson 3 ObjectMapper（解析模型清單 JSON）。 */
    private final ObjectMapper objectMapper;
    /** 環境變數預設模型（spring.ai.openai.chat.model，供留空回退顯示）。 */
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
     * @return 模型名 Optional（未設定為 empty）
     */
    @Transactional(readOnly = true)
    public Optional<String> getAiChatModel() {
        return repository.findBySettingKey(KEY_AI_CHAT_MODEL)
                .map(SystemSetting::getSettingValue)
                .filter(v -> v != null && !v.isBlank());
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
     * 解析模型清單 JSON；任何錯誤回空清單並記 log。
     *
     * @param json JSON 陣列字串
     * @return 模型名清單
     */
    private List<String> parseOptions(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (RuntimeException e) {
            log.warn("模型清單 JSON 解析失敗，回空清單：{}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 回傳目前模型對應的 ChatOptions；未設定模型時回 null（呼叫端不覆蓋，沿用環境變數預設）。
     *
     * @return OpenAiChatOptions 或 null
     */
    @Transactional(readOnly = true)
    public ChatOptions resolveChatOptions() {
        return getAiChatModel()
                .<ChatOptions>map(m -> OpenAiChatOptions.builder().model(m).build())
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
     * upsert AI 設定：驗證 model 為空或在 options 內，否則拋例外；兩個 key 各 upsert。
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
```

- [ ] **Step 5: 跑測試確認通過**

Run:
```powershell
mvn -pl backend test -Dtest=SystemSettingServiceTest
```
Expected: PASS（10 個測試全綠）。

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/SystemSettingService.java backend/src/main/java/com/aicrm/crm/api/Dtos.java backend/src/test/java/com/aicrm/crm/service/SystemSettingServiceTest.java
git commit -m "feat(settings): SystemSettingService 模型讀寫與回退解析 + 單元測試"
```

---

## Task 3: AdminSettingController（GET/PUT + 權限測試）

**Files:**
- Create: `backend/src/main/java/com/aicrm/crm/api/AdminSettingController.java`
- Test: `backend/src/test/java/com/aicrm/crm/api/AdminSettingControllerTest.java`

- [ ] **Step 1: 寫失敗測試**

建立 `backend/src/test/java/com/aicrm/crm/api/AdminSettingControllerTest.java`：

```java
package com.aicrm.crm.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicrm.crm.api.Dtos.AiSettingsRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * AdminSettingController 測試：權限（非 ADMIN 403）、GET 回種子、PUT upsert 後 GET 一致、
 * PUT model 不在 options 回 400。沿用 AdminUserControllerTest 的 MockMvc + @WithMockUser pattern。
 */
@SpringBootTest
class AdminSettingControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @WithMockUser(username = "sales", roles = {"SALES"})
    void nonAdminForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/settings/ai"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getReturnsSeededOptions() throws Exception {
        mockMvc.perform(get("/api/admin/settings/ai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelOptions").isArray())
                .andExpect(jsonPath("$.envDefaultModel").exists());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void putThenGetReflectsChange() throws Exception {
        var req = new AiSettingsRequest("gpt-4o-mini", List.of("gpt-4o-mini", "gemini-3.1-flash-lite-preview"));
        mockMvc.perform(put("/api/admin/settings/ai")
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/settings/ai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentModel").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.source").value("DB"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void putRejectsModelNotInOptions() throws Exception {
        var req = new AiSettingsRequest("ghost", List.of("gpt-4o-mini"));
        mockMvc.perform(put("/api/admin/settings/ai")
                        .content(objectMapper.writeValueAsString(req))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: 跑測試確認失敗**

Run:
```powershell
mvn -pl backend test -Dtest=AdminSettingControllerTest
```
Expected: 失敗（`AdminSettingController` 尚未建立，GET 回 404）。

- [ ] **Step 3: 實作 Controller**

建立 `backend/src/main/java/com/aicrm/crm/api/AdminSettingController.java`：

```java
package com.aicrm.crm.api;

import com.aicrm.crm.api.Dtos.AiSettingsRequest;
import com.aicrm.crm.api.Dtos.AiSettingsResponse;
import com.aicrm.crm.service.JwtService;
import com.aicrm.crm.service.SystemSettingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 系統設定 API（限 ADMIN）：目前提供 AI 對話模型設定的讀取與更新。
 */
@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingController {

    /** 系統設定服務。 */
    private final SystemSettingService systemSettings;

    public AdminSettingController(SystemSettingService systemSettings) {
        this.systemSettings = systemSettings;
    }

    /**
     * 取得 AI 設定檢視（目前模型、候選清單、環境變數預設、來源）。
     *
     * @return AI 設定回應
     */
    @GetMapping("/ai")
    public AiSettingsResponse getAiSettings() {
        return systemSettings.getAiSettingsView();
    }

    /**
     * 更新 AI 設定（model 與候選清單）；model 不在清單內回 400。
     *
     * @param request 設定更新請求
     * @param authentication 登入認證（記錄修改者）
     * @return 更新後的設定檢視
     */
    @PutMapping("/ai")
    public AiSettingsResponse updateAiSettings(@RequestBody AiSettingsRequest request, Authentication authentication) {
        try {
            systemSettings.updateAiSettings(request.model(), request.modelOptions(), resolveUsername(authentication));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return systemSettings.getAiSettingsView();
    }

    /** 從認證主體解析登入帳號（取不到回 "unknown"）。 */
    private String resolveUsername(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof JwtService.AuthPrincipal principal) {
            return principal.username();
        }
        return authentication != null ? authentication.getName() : "unknown";
    }
}
```

註：`@WithMockUser` 的 principal 不是 `JwtService.AuthPrincipal`，故走 `authentication.getName()` 回 "admin"，測試不依賴此值。

- [ ] **Step 4: 跑測試確認通過**

Run:
```powershell
mvn -pl backend test -Dtest=AdminSettingControllerTest
```
Expected: PASS（4 個測試全綠）。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/api/AdminSettingController.java backend/src/test/java/com/aicrm/crm/api/AdminSettingControllerTest.java
git commit -m "feat(settings): AdminSettingController GET/PUT /api/admin/settings/ai + 權限測試"
```

---

## Task 4: 接線四個 LLM 呼叫點

每個 service 注入 `SystemSettingService`，在 `ChatClient.create(chatModel).prompt()...` 鏈上條件式套用 `resolveChatOptions()`。回退邏輯：options 為 null 時不呼叫 `.options(...)`，沿用環境變數預設模型。

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/service/InsightService.java`
- Modify: `backend/src/main/java/com/aicrm/crm/service/ManagerInsightService.java`
- Modify: `backend/src/main/java/com/aicrm/crm/service/SentimentIntentService.java`

- [ ] **Step 1: InsightService 注入 SystemSettingService**

在 `InsightService.java` 加欄位（在 `chatMemory` 欄位附近）：

```java
    /** 系統設定服務：提供 AI 模型覆蓋（DB 設定優先於環境變數）。 */
    private final SystemSettingService systemSettings;
```

修改建構子：在參數列加入 `SystemSettingService systemSettings`（放在 `ChatMemoryService chatMemory` 之後、`@Value(...) String openAiApiKey` 之前），並在 body 加：

```java
        this.systemSettings = systemSettings;
```

- [ ] **Step 2: InsightService 同步呼叫點（callLlm，約第 428 行）加 options**

把：

```java
            var chatResponse = ChatClient.create(chatModel)
                    .prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .chatResponse();
```

改為：

```java
            var spec = ChatClient.create(chatModel).prompt().system(SYSTEM_PROMPT).user(userPrompt);
            var options = systemSettings.resolveChatOptions();
            if (options != null) {
                spec = spec.options(options);
            }
            var chatResponse = spec.call().chatResponse();
```

- [ ] **Step 3: InsightService 串流呼叫點（streamAnswer，約第 237 行）加 options**

把：

```java
        ChatClient.create(chatModel).prompt().system(SYSTEM_PROMPT).user(userPrompt).stream().chatResponse()
                .subscribe(
```

改為：

```java
        var streamSpec = ChatClient.create(chatModel).prompt().system(SYSTEM_PROMPT).user(userPrompt);
        var streamOptions = systemSettings.resolveChatOptions();
        if (streamOptions != null) {
            streamSpec = streamSpec.options(streamOptions);
        }
        streamSpec.stream().chatResponse()
                .subscribe(
```

- [ ] **Step 4: ManagerInsightService 注入 + 呼叫點（約第 160 行）加 options**

加欄位：

```java
    /** 系統設定服務：提供 AI 模型覆蓋（DB 設定優先於環境變數）。 */
    private final SystemSettingService systemSettings;
```

建構子加參數 `SystemSettingService systemSettings`（放在 `ObjectProvider<ChatModel> chatModelProvider` 之後、`@Value(...)` 之前）與 body `this.systemSettings = systemSettings;`。

把：

```java
            var chatResponse = ChatClient.create(chatModel).prompt()
                    .system(SYSTEM_PROMPT).user(userPrompt).call().chatResponse();
```

改為：

```java
            var spec = ChatClient.create(chatModel).prompt().system(SYSTEM_PROMPT).user(userPrompt);
            var options = systemSettings.resolveChatOptions();
            if (options != null) {
                spec = spec.options(options);
            }
            var chatResponse = spec.call().chatResponse();
```

- [ ] **Step 5: SentimentIntentService 注入 + 呼叫點（約第 128 行）加 options**

加欄位：

```java
    /** 系統設定服務：提供 AI 模型覆蓋（DB 設定優先於環境變數）。 */
    private final SystemSettingService systemSettings;
```

建構子加參數 `SystemSettingService systemSettings`（放在 `ObjectMapper objectMapper` 之後、`@Value(...)` 之前）與 body `this.systemSettings = systemSettings;`。

把：

```java
            var chatResponse = ChatClient.create(chatModel)
                    .prompt()
                    .system(SYSTEM_PROMPT)
                    .user(content == null ? "" : content)
                    .call()
                    .chatResponse();
```

改為：

```java
            var spec = ChatClient.create(chatModel).prompt()
                    .system(SYSTEM_PROMPT).user(content == null ? "" : content);
            var options = systemSettings.resolveChatOptions();
            if (options != null) {
                spec = spec.options(options);
            }
            var chatResponse = spec.call().chatResponse();
```

- [ ] **Step 6: 跑相關既有測試確認不破壞**

Run:
```powershell
mvn -pl backend test -Dtest=InsightServiceTest,ManagerInsightServiceTest,SentimentIntentServiceTest
```
Expected: PASS。既有測試的 service 建構子需多傳一個 `SystemSettingService`；若測試以 `new XxxService(...)` 直接建立，需在測試中補 mock。

> **注意（修測試）**：若上述測試以建構子直接 new 出 service，會因簽章變更編譯失敗。修法：在該測試補 `SystemSettingService systemSettings = mock(SystemSettingService.class);`，並對其 `when(systemSettings.resolveChatOptions()).thenReturn(null);`（讓行為等同回退環境變數，不改變既有斷言），再把它傳入建構子。若為 `@SpringBootTest` 注入則由 Spring 自動裝配，無需改。先跑一次看編譯錯誤再決定。

- [ ] **Step 7: 全後端測試**

Run:
```powershell
mvn -pl backend test
```
Expected: BUILD SUCCESS（含新表 Flyway migration 在測試啟動時套用成功）。

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/service/InsightService.java backend/src/main/java/com/aicrm/crm/service/ManagerInsightService.java backend/src/main/java/com/aicrm/crm/service/SentimentIntentService.java
git add backend/src/test/java/com/aicrm/crm/service/InsightServiceTest.java backend/src/test/java/com/aicrm/crm/service/ManagerInsightServiceTest.java backend/src/test/java/com/aicrm/crm/service/SentimentIntentServiceTest.java
git commit -m "feat(settings): AI 呼叫優先套用 DB 設定模型(回退環境變數)"
```

---

## Task 5: 前端型別與 API client

**Files:**
- Modify: `frontend/src/types.ts`
- Modify: `frontend/src/api.ts`

- [ ] **Step 1: 新增型別**

在 `frontend/src/types.ts` 末端加入：

```ts
/** AI 設定回應：currentModel 空字串代表用環境變數；source 為 "DB" | "ENV"。 */
export interface AiSettingsResponse {
  currentModel: string;
  modelOptions: string[];
  envDefaultModel: string;
  source: "DB" | "ENV";
}
```

- [ ] **Step 2: 新增 API 函式**

在 `frontend/src/api.ts` 末端加入（並在檔案頂部的 `import type { ... } from "./types"` 補上 `AiSettingsResponse`）：

```ts
/** 取得 AI 設定（限 ADMIN）。 */
export async function fetchAiSettings() {
  const { data } = await apiClient.get<AiSettingsResponse>("/admin/settings/ai");
  return data;
}

/** 更新 AI 設定（限 ADMIN），回傳更新後的設定。 */
export async function saveAiSettings(model: string, modelOptions: string[]) {
  const { data } = await apiClient.put<AiSettingsResponse>("/admin/settings/ai", { model, modelOptions });
  return data;
}
```

- [ ] **Step 3: 型別檢查**

Run:
```powershell
cd frontend; pnpm exec tsc --noEmit; cd ..
```
Expected: 無型別錯誤（注意：若 `tsc` 無法單獨跑，改用 `pnpm build` 在後續步驟一起驗）。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/types.ts frontend/src/api.ts
git commit -m "feat(settings): 前端 AI 設定型別與 API client"
```

---

## Task 6: 前端設定頁與導覽

**Files:**
- Create: `frontend/src/features/admin/AdminSettingsPage.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/components/AppShell.tsx`

- [ ] **Step 1: 建立 AdminSettingsPage**

建立 `frontend/src/features/admin/AdminSettingsPage.tsx`（沿用 AdminUsersPage 的 role 守衛與 load/error 慣例、className 慣例）：

```tsx
import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { fetchAiSettings, saveAiSettings } from "../../api";
import type { AiSettingsResponse } from "../../types";

/**
 * 系統設定頁（限 ADMIN）：設定 AI 對話模型。
 * 下拉選目前模型（選項來自候選清單）；可新增/刪除候選模型；留空代表使用環境變數預設。
 */
export default function AdminSettingsPage() {
  const { user } = useAuth();

  const [settings, setSettings] = useState<AiSettingsResponse | null>(null);
  const [currentModel, setCurrentModel] = useState("");
  const [options, setOptions] = useState<string[]>([]);
  const [newModel, setNewModel] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionMsg, setActionMsg] = useState<string | null>(null);

  // 非 ADMIN 直接導回儀表板（與 AdminUsersPage 一致）
  if (user?.role !== "ADMIN") {
    return <Navigate to="/dashboard" replace />;
  }

  // 載入設定
  async function load() {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchAiSettings();
      setSettings(data);
      setCurrentModel(data.currentModel);
      setOptions(data.modelOptions);
    } catch (e) {
      setError(e instanceof Error ? e.message : "載入失敗");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  // 新增候選模型（去重、非空）
  function addModel() {
    const m = newModel.trim();
    if (!m || options.includes(m)) {
      return;
    }
    setOptions([...options, m]);
    setNewModel("");
  }

  // 刪除候選模型；若刪掉的是目前選用模型，currentModel 退回空（用環境變數）
  function removeModel(m: string) {
    setOptions(options.filter((o) => o !== m));
    if (currentModel === m) {
      setCurrentModel("");
    }
  }

  // 儲存
  async function save() {
    setError(null);
    setActionMsg(null);
    try {
      const data = await saveAiSettings(currentModel, options);
      setSettings(data);
      setCurrentModel(data.currentModel);
      setOptions(data.modelOptions);
      setActionMsg("已儲存，AI 呼叫即時生效。");
    } catch (e) {
      setError(e instanceof Error ? e.message : "儲存失敗");
    }
  }

  return (
    <div className="admin-page">
      <header className="admin-page__header">
        <h1>系統設定</h1>
        <p className="admin-page__subtitle">設定 AI 對話模型；留空則使用環境變數預設模型。</p>
      </header>

      {error && <div className="admin-alert admin-alert--error">{error}</div>}
      {actionMsg && <div className="admin-alert admin-alert--ok">{actionMsg}</div>}

      {loading ? (
        <p className="admin-muted">載入中…</p>
      ) : (
        <section className="admin-card">
          <label>
            目前模型
            <select value={currentModel} onChange={(e) => setCurrentModel(e.target.value)}>
              <option value="">（使用環境變數預設：{settings?.envDefaultModel || "未設定"}）</option>
              {options.map((o) => (
                <option key={o} value={o}>{o}</option>
              ))}
            </select>
          </label>

          <p className="admin-muted">
            目前來源：{currentModel ? `系統設定（${currentModel}）` : `環境變數（${settings?.envDefaultModel || "未設定"}）`}
          </p>

          <h2>候選模型清單</h2>
          <ul>
            {options.map((o) => (
              <li key={o}>
                {o}
                <button type="button" className="btn btn--ghost" onClick={() => removeModel(o)}>刪除</button>
              </li>
            ))}
          </ul>

          <div>
            <input
              value={newModel}
              placeholder="輸入模型名，如 gpt-4o-mini"
              onChange={(e) => setNewModel(e.target.value)}
            />
            <button type="button" className="btn btn--ghost" onClick={addModel}>新增模型</button>
          </div>

          <button type="button" className="btn btn--primary" onClick={save}>儲存</button>
        </section>
      )}
    </div>
  );
}
```

- [ ] **Step 2: 註冊路由**

在 `frontend/src/App.tsx`：頂部加 import：

```tsx
import AdminSettingsPage from "./features/admin/AdminSettingsPage";
```

在 `<Route path="/admin/users" element={<AdminUsersPage />} />` 之後加：

```tsx
<Route path="/admin/settings" element={<AdminSettingsPage />} />
```

- [ ] **Step 3: 導覽列加入口**

在 `frontend/src/components/AppShell.tsx` 的 `NAV_ITEMS` 陣列中，於帳號管理那列之後加：

```tsx
  { to: "/admin/settings", label: "系統設定", roles: ["ADMIN"] },
```

- [ ] **Step 4: 前端建置驗證**

Run:
```powershell
cd frontend; pnpm build; cd ..
```
Expected: build 成功，無 TypeScript 錯誤。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/admin/AdminSettingsPage.tsx frontend/src/App.tsx frontend/src/components/AppShell.tsx
git commit -m "feat(settings): 前端系統設定頁(AI 模型) + 路由 + 導覽"
```

---

## Task 7: 端到端手動驗證

- [ ] **Step 1: 啟動後端**

Run（背景）：
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -pl backend spring-boot:run
```

- [ ] **Step 2: 啟動前端**

Run（另一終端，背景）：
```powershell
cd frontend; pnpm dev
```

- [ ] **Step 3: 以 ADMIN 登入並驗證**

依瀏覽器自動化規範（CLAUDE.md），寫一支可重跑腳本放在 `frontend/e2e/`（沿用既有 e2e 慣例）驗證：
1. ADMIN 登入後導覽列出現「系統設定」；SALES 登入則不出現。
2. 進 `/admin/settings`，下拉可選候選模型；新增一個模型、選它、儲存 → 重整後仍顯示該模型、來源為「系統設定」。
3. 把目前模型改回「（使用環境變數預設）」、儲存 → 來源顯示「環境變數」。

> 若專案已有 e2e 煙霧測試入口（參考最近 commit 394ec94），在其中追加本頁案例即可，不另起框架。

- [ ] **Step 4: 確認 AI 呼叫實際採用設定模型**

在 `/admin/settings` 設一個與環境變數不同、但帳號可用的模型，於客戶頁觸發 AI 對話/評估，檢查 `ai_call_log` 的 model 欄位是否為新設定模型（DB 設定生效）；再清空設定，確認回退為環境變數模型。

---

## Self-Review（對照 spec 檢查）

- **spec「只改 model，base-url/api-key 固定」** → Task 4 僅覆蓋 `OpenAiChatOptions.model()`，未動 bean 與 application.yml。✓
- **spec「優先 DB、沒有才環境變數」** → `resolveChatOptions()` 空模型回 null、呼叫端不覆蓋。✓（Task 2 測試 + Task 4 接線）
- **spec「即時生效 + 通用 system_settings 表」** → 每次呼叫讀 DB；Task 1 通用 key-value 表。✓
- **spec「ADMIN-only /admin/settings 頁」** → Task 3 沿用 `/api/admin/**` 規則；Task 6 前端 role 守衛 + 導覽 roles。✓
- **spec「下拉選 model + 自訂清單」** → Task 6 select + 新增/刪除。✓
- **spec「種子清單 gemini-3.1-flash-lite-preview, gpt-4o-mini」** → Task 1 種子。✓
- **spec「envDefaultModel 唯讀顯示 + 來源標示」** → Task 2 `getAiSettingsView` + Task 6 顯示。✓
- **spec 測試項（service 解析/回退、controller 403/一致/400、既有 fallback 不破壞）** → Task 2/3/4。✓
- **型別一致性**：`AiSettingsResponse(currentModel, modelOptions, envDefaultModel, source)`、`AiSettingsRequest(model, modelOptions)`、`resolveChatOptions()`、`getAiSettingsView()`、`getAiChatModel()`、`getModelOptions()`、`updateAiSettings(model, options, username)` 跨後端/前端/測試一致。✓
- **Migration 版本**：最新業務 migration 為 V15，本計畫新表用 **V16**。✓

無 placeholder；所有步驟含完整程式碼與指令。
```
