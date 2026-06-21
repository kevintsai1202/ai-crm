# AI 功能一致化（標示 + 彈窗 + AI 歷程）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把團隊診斷、業務 coaching、全公司 Portfolio 評估的 AI 體驗，對齊客戶工作區的「AI 標示 + 點擊彈窗 + AI 歷程」三件套。

**Architecture:** 後端為 `ai_call_log` 加 `subject` 欄（V15），OWNER_COACHING 記 ownerName 以分業務查歷程；新增依類型/業務查歷程的 service 方法與三個唯讀端點。前端新增通用 `AiCallHistoryModal` 與 `ManagerInsightModal`（把現有 inline `InsightPanel` 邏輯搬進彈窗），改版 `/team`（topbar 按鈕 + 表格列按鈕），並在儀表板加「AI 歷程」按鈕。

**Tech Stack:** 後端 Java 21 / Spring Boot 4.1 / JPA / Flyway / JUnit5 + Testcontainers(PostgresTestBase)。前端 React 19 / TS / Vite / react-markdown + remark-gfm / Playwright。

**關鍵事實（實作前先確認）：**
- `ai_call_log` 的類型欄位實體名為 `callType`、DB 欄名為 **`call_type`**（見 `AiCallLog.java`）。
- `AiCallLog` 既有建構子 9 參數：`(customerId, callType, model, promptTokens, completionTokens, totalTokens, aiEnabled, piiMasked, answer)`，內部 `createdAt = Instant.now()`。
- `AiGovernanceService.record(...)` 為 9 參數、`@Transactional(REQUIRES_NEW)`；AiCallLog→AiCallHistoryItem 映射在 `customerCallHistory()`。
- `Dtos.AiCallHistoryItem(Long id, String callType, String model, boolean aiEnabled, int totalTokens, String answer, Instant createdAt)`。

---

## 檔案結構

### 後端
- Create `backend/src/main/resources/db/migration/V15__add_ai_call_log_subject.sql`
- Modify `backend/src/main/java/com/aicrm/crm/domain/AiCallLog.java`（加 `subject` 欄 + 新建構子 + getter）
- Modify `backend/src/main/java/com/aicrm/crm/service/AiGovernanceService.java`（record 多載 + historyByType/historyByOwner）
- Modify `backend/src/main/java/com/aicrm/crm/repository/AiCallLogRepository.java`（兩個查詢）
- Modify `backend/src/main/java/com/aicrm/crm/service/ManagerInsightService.java`（OWNER_COACHING 帶 subject）
- Modify `backend/src/main/java/com/aicrm/crm/api/ManagerInsightController.java`（team/owner calls 端點）
- Modify `backend/src/main/java/com/aicrm/crm/api/AiController.java`（portfolio calls 端點）
- Test: `backend/src/test/java/com/aicrm/crm/service/AiInsightHistoryTest.java`
- Test: 擴充 `backend/src/test/java/com/aicrm/crm/api/ManagerAnalyticsSecurityTest.java`

### 前端
- Create `frontend/src/components/common/AiCallHistoryModal.tsx`
- Create `frontend/src/features/team/ManagerInsightModal.tsx`
- Modify `frontend/src/features/team/TeamAnalyticsPage.tsx`（移除 InsightPanel，改 topbar/表格列按鈕開 modal）
- Modify `frontend/src/features/dashboard/DashboardPage.tsx`（加 AI 歷程按鈕）
- Modify `frontend/src/api.ts`（3 個 fetch）
- Modify `frontend/src/lib/format.ts`? 不需要。
- Test: 擴充 `frontend/e2e/team-analytics.spec.ts`

---

## 後端

### Task 1：V15 migration + AiCallLog.subject + record 多載

**Files:**
- Create: `backend/src/main/resources/db/migration/V15__add_ai_call_log_subject.sql`
- Modify: `backend/src/main/java/com/aicrm/crm/domain/AiCallLog.java`
- Modify: `backend/src/main/java/com/aicrm/crm/service/AiGovernanceService.java`

- [ ] **Step 1: 建立 V15 migration**

```sql
-- AI 功能一致化：ai_call_log 加 subject 維度。
-- OWNER_COACHING 存 ownerName；TEAM_ANALYSIS / PORTFOLIO / 客戶呼叫皆為 null（客戶用 customer_id）。
ALTER TABLE ai_call_log ADD COLUMN subject VARCHAR(255);

-- 依類型 + subject 查歷程
CREATE INDEX idx_ai_call_log_calltype_subject ON ai_call_log (call_type, subject);
```

- [ ] **Step 2: AiCallLog 加 subject 欄 + 新建構子 + getter**

在 `answer` 欄位之後（約 L61）新增欄位：

```java
    /** 非客戶維度的分群鍵（OWNER_COACHING 存 ownerName；其餘為 null）。 */
    @Column(length = 255)
    private String subject;
```

把既有 9 參數建構子改為委派新建構子（保留簽章不變，subject 傳 null）：

```java
    public AiCallLog(Long customerId, AiCallType callType, String model,
                     int promptTokens, int completionTokens, int totalTokens,
                     boolean aiEnabled, boolean piiMasked, String answer) {
        this(customerId, callType, null, model, promptTokens, completionTokens, totalTokens, aiEnabled, piiMasked, answer);
    }

    /**
     * 建立 AI 呼叫紀錄（含 subject 維度）。
     *
     * @param customerId 客戶 ID（可為 null）
     * @param callType 呼叫類型
     * @param subject 分群鍵（OWNER_COACHING 存 ownerName；其餘 null）
     * @param model 模型名稱（可為 null）
     * @param promptTokens 提示 token 數
     * @param completionTokens 完成 token 數
     * @param totalTokens 總 token 數
     * @param aiEnabled 是否真實呼叫 LLM
     * @param piiMasked 是否已遮罩 PII
     * @param answer 回答內容
     */
    public AiCallLog(Long customerId, AiCallType callType, String subject, String model,
                     int promptTokens, int completionTokens, int totalTokens,
                     boolean aiEnabled, boolean piiMasked, String answer) {
        this.customerId = customerId;
        this.callType = callType;
        this.subject = subject;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.aiEnabled = aiEnabled;
        this.piiMasked = piiMasked;
        this.answer = answer;
        this.createdAt = Instant.now();
    }
```

在 getter 區（約 L108）加：

```java
    public String getSubject() { return subject; }
```

- [ ] **Step 3: AiGovernanceService.record 多載**

把既有 `record(...)`（9 參數）改為委派新 10 參數版本（subject=null），並新增 10 參數版本。將原方法本體替換為：

```java
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiCallLog record(AiCallType type, Long customerId, String model,
                            Integer promptTokens, Integer completionTokens, Integer totalTokens,
                            boolean aiEnabled, boolean masked, String answer) {
        return record(type, customerId, null, model, promptTokens, completionTokens, totalTokens, aiEnabled, masked, answer);
    }

    /**
     * 記錄一次 AI 呼叫（含 subject 維度，供 OWNER_COACHING 分業務）。
     *
     * @param type 呼叫類型
     * @param customerId 客戶 ID（可為 null）
     * @param subject 分群鍵（OWNER_COACHING 存 ownerName；其餘 null）
     * @param model 模型名稱（fallback 為 null）
     * @param promptTokens 提示 token 數
     * @param completionTokens 完成 token 數
     * @param totalTokens 總 token 數
     * @param aiEnabled 是否真實呼叫 LLM
     * @param masked 是否已遮罩 PII
     * @param answer 回答內容
     * @return 已儲存的呼叫紀錄
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiCallLog record(AiCallType type, Long customerId, String subject, String model,
                            Integer promptTokens, Integer completionTokens, Integer totalTokens,
                            boolean aiEnabled, boolean masked, String answer) {
        var log = new AiCallLog(
                customerId, type, subject, model,
                promptTokens == null ? 0 : promptTokens,
                completionTokens == null ? 0 : completionTokens,
                totalTokens == null ? 0 : totalTokens,
                aiEnabled, masked, answer);
        return callLogRepository.save(log);
    }
```

- [ ] **Step 4: 編譯**

Run:
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; mvn -pl backend -q compile; "EXIT=$LASTEXITCODE"
```
Expected: `EXIT=0`。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V15__add_ai_call_log_subject.sql backend/src/main/java/com/aicrm/crm/domain/AiCallLog.java backend/src/main/java/com/aicrm/crm/service/AiGovernanceService.java
git commit -m "feat(ai): ai_call_log 加 subject 欄 + record 多載(V15)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2：歷程查詢（repo + service + OWNER_COACHING 帶 subject）— TDD

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/repository/AiCallLogRepository.java`
- Modify: `backend/src/main/java/com/aicrm/crm/service/AiGovernanceService.java`
- Modify: `backend/src/main/java/com/aicrm/crm/service/ManagerInsightService.java`
- Test: `backend/src/test/java/com/aicrm/crm/service/AiInsightHistoryTest.java`

- [ ] **Step 1: 寫失敗測試**

```java
package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.domain.AiCallType;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.support.PostgresTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * AI 歷程查詢測試：團隊診斷依類型、業務 coaching 依 subject(ownerName) 隔離。
 */
class AiInsightHistoryTest extends PostgresTestBase {

    @Autowired ManagerInsightService insightService;
    @Autowired AiGovernanceService governance;
    @Autowired CustomerRepository customers;

    @Test
    void teamHistory_byType_andOwnerHistory_bySubject() {
        // 取兩個不同業務名（種子資料保證 >= 2）
        var owners = customers.findDistinctOwners();
        assertThat(owners.size()).isGreaterThanOrEqualTo(2);
        var ownerA = owners.get(0);
        var ownerB = owners.get(1);

        insightService.generateTeamInsight();          // 寫一筆 TEAM_ANALYSIS（subject null）
        insightService.generateOwnerInsight(ownerA);   // 寫一筆 OWNER_COACHING（subject=ownerA）
        insightService.generateOwnerInsight(ownerB);   // 寫一筆 OWNER_COACHING（subject=ownerB）

        // 團隊歷程：依類型，至少一筆，且都是 TEAM 標籤
        var teamCalls = governance.historyByType(AiCallType.TEAM_ANALYSIS);
        assertThat(teamCalls).isNotEmpty();
        assertThat(teamCalls).allMatch(c -> c.callType().equals("TEAM_ANALYSIS"));

        // 業務歷程：A 只看到 A、B 只看到 B（subject 隔離）
        List<?> aCalls = governance.historyByOwner(ownerA);
        List<?> bCalls = governance.historyByOwner(ownerB);
        assertThat(aCalls).hasSize(1);
        assertThat(bCalls).hasSize(1);
    }
}
```

- [ ] **Step 2: 執行確認失敗**

Run:
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; mvn -pl backend test -Dtest=AiInsightHistoryTest
```
Expected: 編譯/測試失敗（`historyByType` / `historyByOwner` 不存在）。

- [ ] **Step 3: AiCallLogRepository 加查詢**

在介面內（`findByCustomerIdOrderByCreatedAtDesc` 之後）加：

```java
    /**
     * 依類型查無客戶、無 subject 的呼叫紀錄（供 TEAM_ANALYSIS / PORTFOLIO 歷程）。
     *
     * @param type 呼叫類型
     * @return 該類型歷次呼叫（新到舊）
     */
    List<AiCallLog> findByCallTypeAndCustomerIdIsNullAndSubjectIsNullOrderByCreatedAtDesc(AiCallType type);

    /**
     * 依類型 + subject 查呼叫紀錄（供 OWNER_COACHING 分業務歷程）。
     *
     * @param type 呼叫類型
     * @param subject 分群鍵（ownerName）
     * @return 該業務歷次呼叫（新到舊）
     */
    List<AiCallLog> findByCallTypeAndSubjectOrderByCreatedAtDesc(AiCallType type, String subject);
```

並在檔案 import 區加 `import com.aicrm.crm.domain.AiCallType;`。

- [ ] **Step 4: AiGovernanceService 加 historyByType / historyByOwner**

在 `customerCallHistory(...)` 之後加（沿用相同映射）：

```java
    /**
     * 依類型列出無客戶、無 subject 的 AI 呼叫歷程（TEAM_ANALYSIS / PORTFOLIO）。
     *
     * @param type 呼叫類型
     * @return AI 呼叫歷史清單（新到舊）
     */
    @Transactional(readOnly = true)
    public java.util.List<Dtos.AiCallHistoryItem> historyByType(AiCallType type) {
        return callLogRepository.findByCallTypeAndCustomerIdIsNullAndSubjectIsNullOrderByCreatedAtDesc(type).stream()
                .map(c -> new Dtos.AiCallHistoryItem(c.getId(), c.getCallType().name(), c.getModel(),
                        c.isAiEnabled(), c.getTotalTokens(), c.getAnswer(), c.getCreatedAt()))
                .toList();
    }

    /**
     * 列出指定業務的 OWNER_COACHING AI 呼叫歷程。
     *
     * @param ownerName 業務顯示名稱（subject）
     * @return AI 呼叫歷史清單（新到舊）
     */
    @Transactional(readOnly = true)
    public java.util.List<Dtos.AiCallHistoryItem> historyByOwner(String ownerName) {
        return callLogRepository.findByCallTypeAndSubjectOrderByCreatedAtDesc(AiCallType.OWNER_COACHING, ownerName).stream()
                .map(c -> new Dtos.AiCallHistoryItem(c.getId(), c.getCallType().name(), c.getModel(),
                        c.isAiEnabled(), c.getTotalTokens(), c.getAnswer(), c.getCreatedAt()))
                .toList();
    }
```

- [ ] **Step 5: ManagerInsightService 的 OWNER_COACHING 帶 subject**

`ManagerInsightService` 的 `callLlm(...)` 目前對所有類型用 9 參數 `record`。為了讓 OWNER_COACHING 帶 subject，最小改法：在 `generateOwnerInsight` 與 `generateTeamInsight` 各自的呼叫鏈傳入 subject。將 `callLlm` 簽章加一個 `subject` 參數，內部三處 `aiGovernance.record(type, null, ...)` 改為 `aiGovernance.record(type, null, subject, ...)`（使用 Task 1 的 10 參數多載）。

具體：把 `private LlmResult callLlm(AiCallType type, String userPrompt, String fallbackAnswer)` 改為 `private LlmResult callLlm(AiCallType type, String subject, String userPrompt, String fallbackAnswer)`，三個 `aiGovernance.record(type, null, ...)`（成功、空白、例外）改為 `aiGovernance.record(type, null, subject, ...)`。呼叫端：
- `generateTeamInsight`：`callLlm(AiCallType.TEAM_ANALYSIS, null, prompt, fallback)`
- `generateOwnerInsight`：`callLlm(AiCallType.OWNER_COACHING, owner, prompt, fallback)`

- [ ] **Step 6: 執行測試確認通過**

Run:
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; mvn -pl backend test -Dtest=AiInsightHistoryTest,ManagerInsightServiceTest
```
Expected: 兩個測試類別全綠（`ManagerInsightServiceTest` 確保改 `callLlm` 簽章未破壞既有行為）。

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/repository/AiCallLogRepository.java backend/src/main/java/com/aicrm/crm/service/AiGovernanceService.java backend/src/main/java/com/aicrm/crm/service/ManagerInsightService.java backend/src/test/java/com/aicrm/crm/service/AiInsightHistoryTest.java
git commit -m "feat(ai): 依類型/業務查 AI 歷程 + OWNER_COACHING 記 subject(含測試)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3：歷程端點 + 權限測試

**Files:**
- Modify: `backend/src/main/java/com/aicrm/crm/api/ManagerInsightController.java`
- Modify: `backend/src/main/java/com/aicrm/crm/api/AiController.java`
- Test: `backend/src/test/java/com/aicrm/crm/api/ManagerAnalyticsSecurityTest.java`

- [ ] **Step 1: ManagerInsightController 加 team/owner calls 端點**

需注入 `AiGovernanceService`。在建構子加入該依賴（與既有 `ManagerInsightService service` 並列），並加兩個端點：

```java
    /**
     * 列出團隊診斷的 AI 歷程（TEAM_ANALYSIS）。
     *
     * @return AI 呼叫歷史清單
     */
    @GetMapping("/team/calls")
    public java.util.List<Dtos.AiCallHistoryItem> teamCalls() {
        return governance.historyByType(com.aicrm.crm.domain.AiCallType.TEAM_ANALYSIS);
    }

    /**
     * 列出指定業務的 coaching AI 歷程（OWNER_COACHING）。
     *
     * @param owner 業務顯示名稱
     * @return AI 呼叫歷史清單
     */
    @GetMapping("/owner/calls")
    public java.util.List<Dtos.AiCallHistoryItem> ownerCalls(@RequestParam String owner) {
        return governance.historyByOwner(owner);
    }
```

（在類別頂部加欄位 `private final com.aicrm.crm.service.AiGovernanceService governance;`，建構子改為 `public ManagerInsightController(ManagerInsightService service, com.aicrm.crm.service.AiGovernanceService governance) { this.service = service; this.governance = governance; }`。）

- [ ] **Step 2: AiController 加 portfolio calls 端點**

AiController 已注入 `aiGovernanceService`。在 `/portfolio/assessment` 端點附近加：

```java
    /**
     * 列出全公司 Portfolio 評估的 AI 歷程（PORTFOLIO）。
     *
     * @return AI 呼叫歷史清單
     */
    @GetMapping("/portfolio/calls")
    public java.util.List<Dtos.AiCallHistoryItem> portfolioCalls() {
        return aiGovernanceService.historyByType(com.aicrm.crm.domain.AiCallType.PORTFOLIO);
    }
```

- [ ] **Step 3: 權限測試（SALES 被擋、MANAGER 可進）**

在 `ManagerAnalyticsSecurityTest` 加：

```java
    @Test
    void sales_isForbidden_onTeamCalls() throws Exception {
        var token = login("sales@aurora.local");
        mockMvc().perform(get("/api/manager/insights/team/calls").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void manager_canAccessTeamCalls() throws Exception {
        var token = login("manager@aurora.local");
        mockMvc().perform(get("/api/manager/insights/team/calls").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
```

- [ ] **Step 4: 執行測試**

Run:
```powershell
$env:JAVA_HOME = "D:\java\jdk-21"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"; mvn -pl backend test -Dtest=ManagerAnalyticsSecurityTest
```
Expected: 全綠（原 4 + 新 2 = 6）。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/aicrm/crm/api/ManagerInsightController.java backend/src/main/java/com/aicrm/crm/api/AiController.java backend/src/test/java/com/aicrm/crm/api/ManagerAnalyticsSecurityTest.java
git commit -m "feat(ai): team/owner/portfolio AI 歷程端點 + 權限測試

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 前端

### Task 4：api.ts 歷程 fetch + 通用 AiCallHistoryModal

**Files:**
- Modify: `frontend/src/api.ts`
- Create: `frontend/src/components/common/AiCallHistoryModal.tsx`

- [ ] **Step 1: api.ts 加三個歷程 fetch**

`AiCallHistoryItem` 已在 `./types` 匯出且 api.ts 已 import（用於 `fetchCustomerAiCalls`）。在 `fetchCustomerAiCalls` 之後加：

```typescript
/**
 * 取得團隊診斷（TEAM_ANALYSIS）的 AI 呼叫歷程。
 */
export async function fetchTeamInsightCalls() {
  const { data } = await apiClient.get<AiCallHistoryItem[]>("/manager/insights/team/calls");
  return data;
}

/**
 * 取得指定業務 coaching（OWNER_COACHING）的 AI 呼叫歷程。
 *
 * @param owner 業務顯示名稱
 */
export async function fetchOwnerInsightCalls(owner: string) {
  const { data } = await apiClient.get<AiCallHistoryItem[]>("/manager/insights/owner/calls", { params: { owner } });
  return data;
}

/**
 * 取得全公司 Portfolio 評估（PORTFOLIO）的 AI 呼叫歷程。
 */
export async function fetchPortfolioCalls() {
  const { data } = await apiClient.get<AiCallHistoryItem[]>("/ai/portfolio/calls");
  return data;
}
```

（確認 api.ts 的 import 區已含 `AiCallHistoryItem`；若無則加入。）

- [ ] **Step 2: 建立通用 AiCallHistoryModal**

```tsx
import { useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type { AiCallHistoryItem } from "../../types";
import { formatDateTime } from "../../lib/format";
import { AiBadge } from "./AiBadge";

/** AI 呼叫類型的中文標籤（涵蓋客戶與管理層各類型）。 */
const CALL_TYPE_LABELS: Record<string, string> = {
  CHAT: "對話",
  ASSESSMENT: "整體評估",
  PORTFOLIO: "全公司評估",
  TEAM_ANALYSIS: "團隊診斷",
  OWNER_COACHING: "業務輔導"
};

/**
 * 通用 AI 歷程 Modal：列出一組 AI 呼叫紀錄（新到舊，可展開看完整答案）。
 * 函式級註解：與客戶版 AiHistoryModal 同樣式，但不含 Agent trace，供團隊診斷 / 業務 coaching / 全公司評估共用。
 *
 * @param title 標題（如「團隊診斷 AI 歷程」）
 * @param calls AI 呼叫歷史清單
 * @param loading 是否載入中
 * @param onClose 關閉 callback
 */
export function AiCallHistoryModal({ title, calls, loading, onClose }: {
  title: string;
  calls: AiCallHistoryItem[];
  loading: boolean;
  onClose: () => void;
}) {
  // 目前展開中的呼叫 id（預設收合，點選展開）
  const [expandedId, setExpandedId] = useState<number | null>(null);

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content report-modal" onClick={(e) => e.stopPropagation()}>
        <div className="report-header">
          <div>
            <h3>{title} <AiBadge onDark /></h3>
            <small>歷次 AI 呼叫紀錄</small>
          </div>
          <button type="button" className="chat-close" onClick={onClose} aria-label="關閉">✕</button>
        </div>
        <div className="report-body">
          <h4 className="ai-history-section">AI 呼叫歷史（{calls.length}）</h4>
          {loading ? (
            <p className="chat-typing">載入中<span>…</span></p>
          ) : calls.length === 0 ? (
            <p className="trace-empty">尚無 AI 呼叫紀錄。點「重新分析」後即會記錄。</p>
          ) : (
            <div className="ai-history-list">
              {calls.map((call) => {
                const open = expandedId === call.id;
                return (
                  <article className={`ai-history-item ${open ? "open" : ""}`} key={call.id}>
                    <button type="button" className="ai-history-head" onClick={() => setExpandedId(open ? null : call.id)}>
                      <span className="ai-history-type">{CALL_TYPE_LABELS[call.callType] ?? call.callType}</span>
                      <span className="ai-history-time">{formatDateTime(call.createdAt)}</span>
                      <span className={`ai-history-mode ${call.aiEnabled ? "real" : "fallback"}`}>
                        {call.aiEnabled ? (call.model ?? "LLM") : "樣板 fallback"}
                      </span>
                      <span className="ai-history-toggle">{open ? "▲" : "▼"}</span>
                    </button>
                    {open ? (
                      <div className="ai-history-answer markdown-body">
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>{call.answer}</ReactMarkdown>
                      </div>
                    ) : null}
                  </article>
                );
              })}
            </div>
          )}
        </div>
        <div className="report-footer">
          <button type="button" onClick={onClose}>關閉</button>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: 型別檢查**

Run: `cd d:\GitHub\ai-crm\frontend; pnpm exec tsc --noEmit`
Expected: exit 0。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api.ts frontend/src/components/common/AiCallHistoryModal.tsx
git commit -m "feat(ai): api 歷程 fetch + 通用 AiCallHistoryModal

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5：ManagerInsightModal + /team 改版

**Files:**
- Create: `frontend/src/features/team/ManagerInsightModal.tsx`
- Modify: `frontend/src/features/team/TeamAnalyticsPage.tsx`

- [ ] **Step 1: 建立 ManagerInsightModal**

```tsx
import { useEffect, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  fetchTeamInsight,
  generateTeamInsight,
  fetchOwnerInsight,
  generateOwnerInsight,
  fetchTeamInsightCalls,
  fetchOwnerInsightCalls
} from "../../api";
import type { ManagerInsightResponse, AiCallHistoryItem } from "../../types";
import { formatDateTime } from "../../lib/format";
import { AiBadge } from "../../components/common/AiBadge";
import { AiCallHistoryModal } from "../../components/common/AiCallHistoryModal";

/**
 * Manager AI 分析彈窗：團隊診斷（scope=TEAM）或個別業務 coaching（scope=OWNER）。
 * 函式級註解：開啟先讀快取顯示報告 + 上次分析時間；「重新分析」呼叫 LLM；「AI 歷程」開歷程彈窗。
 *
 * @param scope TEAM 或 OWNER
 * @param owner OWNER 時的業務名
 * @param onClose 關閉 callback
 */
export function ManagerInsightModal({ scope, owner, onClose }: {
  scope: "TEAM" | "OWNER";
  owner?: string;
  onClose: () => void;
}) {
  const [insight, setInsight] = useState<ManagerInsightResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  // AI 歷程彈窗狀態
  const [historyOpen, setHistoryOpen] = useState(false);
  const [calls, setCalls] = useState<AiCallHistoryItem[]>([]);
  const [callsLoading, setCallsLoading] = useState(false);

  const title = scope === "TEAM" ? "團隊整體診斷" : `${owner} 的輔導報告`;

  // 開啟先讀快取
  useEffect(() => {
    let alive = true;
    setLoading(true);
    setErr(null);
    const p = scope === "TEAM" ? fetchTeamInsight() : fetchOwnerInsight(owner as string);
    p.then((r) => { if (alive) setInsight(r); })
      .catch((e) => { console.error("讀取 AI 分析快取失敗:", e); if (alive) setErr("讀取失敗"); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [scope, owner]);

  /** 點按生成：呼叫 LLM 並更新顯示。 */
  async function handleGenerate() {
    setGenerating(true);
    setErr(null);
    try {
      const r = scope === "TEAM" ? await generateTeamInsight() : await generateOwnerInsight(owner as string);
      setInsight(r);
    } catch (e) {
      console.error("產生 AI 分析失敗:", e);
      setErr("產生失敗，請稍後再試");
    } finally {
      setGenerating(false);
    }
  }

  /** 開啟 AI 歷程：載入對應範圍的歷次呼叫。 */
  async function openHistory() {
    setHistoryOpen(true);
    setCallsLoading(true);
    try {
      const list = scope === "TEAM" ? await fetchTeamInsightCalls() : await fetchOwnerInsightCalls(owner as string);
      setCalls(list);
    } catch (e) {
      console.error("讀取 AI 歷程失敗:", e);
      setCalls([]);
    } finally {
      setCallsLoading(false);
    }
  }

  return (
    <>
      <div className="modal-overlay" onClick={onClose}>
        <div className="modal-content report-modal" onClick={(e) => e.stopPropagation()}>
          <div className="report-header">
            <div>
              <h3>{title} <AiBadge onDark /></h3>
              {insight ? <small>上次分析：{formatDateTime(insight.generatedAt)}{insight.model ? `（${insight.model}）` : "（教學版摘要）"}</small> : null}
            </div>
            <button type="button" className="chat-close" onClick={onClose} aria-label="關閉">✕</button>
          </div>
          <div className="report-body">
            {loading ? (
              <p className="chat-typing">載入中<span>…</span></p>
            ) : err ? (
              <p className="trace-empty">{err}</p>
            ) : insight ? (
              <div className="markdown-body">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{insight.content}</ReactMarkdown>
              </div>
            ) : (
              <p className="trace-empty">尚未產生分析，點「重新分析」由 AI 產出。</p>
            )}
          </div>
          <div className="report-footer">
            <button type="button" className="btn-secondary" onClick={openHistory}>🕘 AI 歷程</button>
            <button type="button" className="btn-assess" disabled={generating} onClick={handleGenerate}>
              {generating ? "分析中…" : "重新分析"}
            </button>
            <button type="button" onClick={onClose}>關閉</button>
          </div>
        </div>
      </div>
      {historyOpen ? (
        <AiCallHistoryModal
          title={`${title} AI 歷程`}
          calls={calls}
          loading={callsLoading}
          onClose={() => setHistoryOpen(false)}
        />
      ) : null}
    </>
  );
}
```

- [ ] **Step 2: 改版 TeamAnalyticsPage（移除 inline InsightPanel，改 modal）**

把 `frontend/src/features/team/TeamAnalyticsPage.tsx` 整檔替換為：

```tsx
import { useEffect, useMemo, useState } from "react";
import { fetchManagerAnalytics } from "../../api";
import type { ManagerAnalyticsResponse } from "../../types";
import { formatCompactMoney } from "../../lib/format";
import { AiBadge } from "../../components/common/AiBadge";
import { ManagerInsightModal } from "./ManagerInsightModal";

/** 業務績效表可排序的欄位鍵。 */
type SortKey = "wonAmount" | "winRate" | "pipelineAmount" | "customerCount" | "highRiskCount";

/**
 * 業務分析頁（MANAGER/ADMIN）：團隊 KPI + 可排序業務績效表 + AI 彈窗（團隊診斷 / 業務 coaching）。
 * 函式級註解：純統計來自 /api/manager/analytics；AI 走彈窗（比照儀表板）——topbar 開團隊診斷，表格列開業務 coaching。
 */
export function TeamAnalyticsPage() {
  const [data, setData] = useState<ManagerAnalyticsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [sortKey, setSortKey] = useState<SortKey>("wonAmount");
  // AI 彈窗狀態：null=未開；{scope, owner?}
  const [modal, setModal] = useState<{ scope: "TEAM" | "OWNER"; owner?: string } | null>(null);

  useEffect(() => {
    void (async () => {
      setLoading(true);
      setError(null);
      try {
        setData(await fetchManagerAnalytics());
      } catch (e) {
        console.error("載入業務分析失敗:", e);
        setError("載入業務分析失敗");
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  // 依選定欄位降序排序（不可變副本）
  const sortedOwners = useMemo(() => {
    if (!data) return [];
    return [...data.owners].sort((a, b) => Number(b[sortKey]) - Number(a[sortKey]));
  }, [data, sortKey]);

  if (loading) return <div className="panel"><div className="sr-empty">載入中…</div></div>;
  if (error || !data) return <div className="panel"><div className="sr-empty">{error ?? "無資料"}</div></div>;

  const t = data.team;
  return (
    <>
      <section className="topbar">
        <div>
          <p>Manager Console</p>
          <h2>業務分析</h2>
        </div>
        <div className="topbar-actions">
          <button type="button" className="btn-assess topbar-assess" onClick={() => setModal({ scope: "TEAM" })}>
            🤖 團隊整體診斷 <AiBadge onDark />
          </button>
        </div>
      </section>

      <div className="team-kpi-row">
        <Kpi label="客戶總數" value={String(t.totalCustomers)} />
        <Kpi label="全團隊成交金額" value={formatCompactMoney(t.totalWonAmount)} />
        <Kpi label="進行中商機" value={formatCompactMoney(t.totalPipeline)} />
        <Kpi label="高風險客戶" value={String(t.totalHighRisk)} />
        <Kpi label="平均成交率" value={`${Math.round(t.avgWinRate * 100)}%`} />
        <Kpi label="業務人數" value={String(t.ownerCount)} />
      </div>

      <div className="panel">
        <div className="panel-head">
          <h3>各業務績效</h3>
          <label className="sort-select">
            排序：
            <select value={sortKey} onChange={(e) => setSortKey(e.target.value as SortKey)}>
              <option value="wonAmount">成交金額</option>
              <option value="winRate">成交率</option>
              <option value="pipelineAmount">進行中商機</option>
              <option value="customerCount">客戶數</option>
              <option value="highRiskCount">高風險數</option>
            </select>
          </label>
        </div>
        <table className="admin-user-table">
          <thead>
            <tr>
              <th>業務</th><th>客戶</th><th>高風險</th><th>進行中商機</th>
              <th>已成交</th><th>成交率</th><th>平均互動間隔</th><th>本季續約</th><th>Coaching</th>
            </tr>
          </thead>
          <tbody>
            {sortedOwners.map((o) => (
              <tr key={o.ownerName}>
                <td>{o.ownerName}</td>
                <td>{o.customerCount}</td>
                <td>{o.highRiskCount}</td>
                <td>{formatCompactMoney(o.pipelineAmount)}（{o.activeOpportunityCount}）</td>
                <td>{formatCompactMoney(o.wonAmount)}（{o.wonCount}）</td>
                <td>{Math.round(o.winRate * 100)}%</td>
                <td>{o.avgDaysSinceInteraction == null ? "—" : `${Math.round(o.avgDaysSinceInteraction)} 天`}</td>
                <td>{o.renewalsThisQuarter}</td>
                <td>
                  <button type="button" className="btn-secondary" onClick={() => setModal({ scope: "OWNER", owner: o.ownerName })}>
                    輔導報告 <AiBadge />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {modal ? (
        <ManagerInsightModal scope={modal.scope} owner={modal.owner} onClose={() => setModal(null)} />
      ) : null}
    </>
  );
}

/** 單一 KPI 卡。 */
function Kpi({ label, value }: { label: string; value: string }) {
  return (
    <div className="kpi-card">
      <span className="kpi-label">{label}</span>
      <strong className="kpi-value">{value}</strong>
    </div>
  );
}
```

- [ ] **Step 3: 型別檢查 + 建置**

Run: `cd d:\GitHub\ai-crm\frontend; pnpm exec tsc --noEmit; pnpm build`
Expected: 皆 exit 0。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/features/team/ManagerInsightModal.tsx frontend/src/features/team/TeamAnalyticsPage.tsx
git commit -m "feat(team): 團隊診斷/業務coaching 改 AI 彈窗(含 AI 歷程)+topbar 按鈕

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6：儀表板加全公司評估 AI 歷程按鈕

**Files:**
- Modify: `frontend/src/features/dashboard/DashboardPage.tsx`

- [ ] **Step 1: import 與狀態**

在 DashboardPage 既有 import 加：
```tsx
import { AiCallHistoryModal } from "../../components/common/AiCallHistoryModal";
import { fetchPortfolioCalls } from "../../api";
import type { AiCallHistoryItem } from "../../types";
```
（若 `AiCallHistoryItem` 已 import 則略過。`fetchPortfolioCalls` 併入既有 `../../api` import。）

在元件內既有 useState 區附近加：
```tsx
  // 全公司評估 AI 歷程彈窗
  const [portfolioHistoryOpen, setPortfolioHistoryOpen] = useState(false);
  const [portfolioCalls, setPortfolioCalls] = useState<AiCallHistoryItem[]>([]);
  const [portfolioCallsLoading, setPortfolioCallsLoading] = useState(false);
```

- [ ] **Step 2: 開啟歷程的 handler**

在 `openPortfolioAssessment` 之後加：
```tsx
  /** 開啟全公司評估的 AI 歷程。 */
  async function openPortfolioHistory() {
    setPortfolioHistoryOpen(true);
    setPortfolioCallsLoading(true);
    try {
      setPortfolioCalls(await fetchPortfolioCalls());
    } catch (e) {
      console.error("讀取全公司評估 AI 歷程失敗:", e);
      setPortfolioCalls([]);
    } finally {
      setPortfolioCallsLoading(false);
    }
  }
```

- [ ] **Step 3: topbar 加按鈕（在既有「整體評估（全公司）」按鈕之後）**

把 [DashboardPage.tsx:286](frontend/src/features/dashboard/DashboardPage.tsx) 的整體評估按鈕之後加一顆：
```tsx
          <button type="button" className="btn-secondary" onClick={openPortfolioHistory}>🕘 AI 歷程</button>
```

- [ ] **Step 4: 掛載歷程彈窗（在既有 ReportModal 渲染之後）**

在 `{report?.open ? <ReportModal .../> : null}` 之後加：
```tsx
      {portfolioHistoryOpen ? (
        <AiCallHistoryModal
          title="全公司評估 AI 歷程"
          calls={portfolioCalls}
          loading={portfolioCallsLoading}
          onClose={() => setPortfolioHistoryOpen(false)}
        />
      ) : null}
```

- [ ] **Step 5: 型別檢查 + 建置**

Run: `cd d:\GitHub\ai-crm\frontend; pnpm exec tsc --noEmit; pnpm build`
Expected: 皆 exit 0。

- [ ] **Step 6: Commit**

```bash
git add frontend/src/features/dashboard/DashboardPage.tsx
git commit -m "feat(dashboard): 全公司評估加 AI 歷程按鈕

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 7：E2E 延伸

**Files:**
- Modify: `frontend/e2e/team-analytics.spec.ts`

- [ ] **Step 1: 把第一個測試的 AI 區塊斷言改為彈窗流程**

將 `team-analytics.spec.ts` 第一個測試（MANAGER）中「AI-A 團隊整體診斷區塊 / 重新分析」相關段落，替換為彈窗流程斷言（KPI、表格、排序段落保留）：

```ts
  // topbar「團隊整體診斷」開彈窗
  await page.locator(".topbar-actions button", { hasText: "團隊整體診斷" }).click();
  const modal = page.locator(".report-modal", { hasText: "團隊整體診斷" });
  await expect(modal).toBeVisible();
  // 重新分析 → 產出報告
  await modal.locator("button", { hasText: "重新分析" }).click();
  await expect(modal.locator(".markdown-body")).toBeVisible({ timeout: 60000 });
  // AI 歷程 → 歷程彈窗出現且至少一筆
  await modal.locator("button", { hasText: "AI 歷程" }).click();
  const history = page.locator(".report-modal", { hasText: "AI 歷程" });
  await expect(history).toBeVisible();
  await expect(history.locator(".ai-history-item").first()).toBeVisible();
  await history.locator(".report-footer button", { hasText: "關閉" }).click();
  // 關閉診斷彈窗
  await modal.locator(".report-footer button", { hasText: "關閉" }).click();

  // 點第一列業務「輔導報告」→ 業務彈窗出現
  await page.locator(".admin-user-table tbody tr").first().locator("button", { hasText: "輔導報告" }).click();
  await expect(page.locator(".report-modal", { hasText: "的輔導報告" })).toBeVisible();
```

（第二個測試「SALES 導回」維持不變。）

- [ ] **Step 2: 執行 e2e（需後端在 18080 跑合併後程式碼、前端 5173）**

Run: `cd d:\GitHub\ai-crm\frontend; pnpm exec playwright test team-analytics --reporter=list`
Expected: 2 passed。（若後端為舊版，先重啟含本功能的後端。）

- [ ] **Step 3: Commit**

```bash
git add frontend/e2e/team-analytics.spec.ts
git commit -m "test(e2e): 團隊診斷/業務coaching 彈窗 + AI 歷程煙霧測試

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 驗收（全部任務後）

- [ ] 後端全測試：`mvn -pl backend test`（設 `$env:APP_SECURITY_JWT_SECRET` ≥32 字元）→ 全綠。
- [ ] 前端：`pnpm exec tsc --noEmit; pnpm build` → 皆 exit 0。
- [ ] e2e：`pnpm exec playwright test team-analytics` → 2 passed。

---

## Self-Review 紀錄

- **Spec 覆蓋**：req1（標示+彈窗）= Task 5/6 的 AiBadge + modal；req2（歷程）= Task 2/3 後端 + Task 4 modal + Task 5/6 接線；req3（團隊放最上方+歷程）= Task 5 topbar 按鈕 + ManagerInsightModal 內 AI 歷程；req4（儀表板全公司評估歷程）= Task 6。
- **欄位名**：V15 與 repo 查詢一律用 `call_type`（實體 `callType`）；index `idx_ai_call_log_calltype_subject`。
- **型別一致**：`historyByType(AiCallType)`、`historyByOwner(String)`、`callLlm(type, subject, prompt, fallback)` 前後一致；前端 `AiCallHistoryModal` props 與 `ManagerInsightModal` 使用一致。
- **不破壞既有**：`AiCallLog` 9 參數建構子、`record` 9 參數簽章皆保留（委派），`InsightService` 不動。
- **無 placeholder**：所有步驟含實際程式碼與指令。
