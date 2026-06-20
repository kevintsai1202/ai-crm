# SP4 設計規格：PII 遮罩 + AI 治理

> 子專案：SP4（路線圖見 `docs/roadmap-progress.md`）
> 建立日期：2026-06-19
> 依據：`docs/consulting-review.md`（中風險 6/7：無 AI 成本治理、PII 未脫敏）、`docs/crm-ai-consultant-analysis.md`（缺點 4：AI 治理不足）

---

## 1. 目標與成功標準

**目標**：送 LLM 前遮罩客戶 PII；每次 LLM 呼叫記錄用量與答案（稽核）；提供使用者對 AI 建議「採納/拒絕」的記錄。

**成功標準**：
1. grounding context 送 LLM 前，email / 電話 / 統編被遮罩（風險計算與 DB 仍用原始值）。
2. 每次 LLM 呼叫（chat / assessment / portfolio）寫一筆 `ai_call_log`：類型、model、prompt/completion/total tokens、是否真實 LLM（vs fallback）、是否遮罩、答案內容。
3. 提供 `POST /api/ai/calls/{id}/feedback`（採納/拒絕）與 `GET /api/ai/usage`（用量彙總，限 MANAGER/ADMIN）。
4. `mvn -pl backend test` 全綠（含新測試）。

**非目標**：
- 不做前端採納/拒絕 UI 串接（後端能力備齊；ChatResponse 回傳 callId 供未來串接）。
- 不做 prompt 版本管理 / 模型參數註冊（後續）。
- 不遮罩知識文件或對話記憶內容（僅客戶 PII 進 prompt 的部分）。

---

## 2. 元件設計

### 2.1 PiiMasker（util）
- `String mask(String text)`：regex 遮罩
  - Email → `e***@***`
  - 電話（09xxxxxxxx / 0x-xxxxxxx）→ `09****` 樣式
  - 統編（8 碼數字）→ `********`
- 純函式、可單元測試。

### 2.2 AiCallLog（entity + V6 表）
`ai_call_log`：id, customer_id(nullable), call_type(CHAT/ASSESSMENT/PORTFOLIO), model, prompt_tokens, completion_tokens, total_tokens, ai_enabled(bool), pii_masked(bool), answer(text), created_at。
`ai_feedback`：id, call_log_id(FK), decision(ADOPTED/REJECTED), note, created_at, created_by。

### 2.3 AiGovernanceService
- `AiCallLog record(callType, customerId, model, usage, aiEnabled, masked, answer)`：寫日誌，回傳含 id 的實體。
- `void feedback(callLogId, decision, note, user)`：寫 ai_feedback。
- `UsageSummary usage()`：彙總 total calls / total tokens / by model / adopted vs rejected。

### 2.4 InsightService 整併
- grounding context 組裝時：客戶 PII 欄位先過 `PiiMasker`（email/phone/taxId）；標記 `masked=true`。
- LLM 呼叫由 `.call().content()` 改 `.call().chatResponse()`：取 `getResult().getOutput().getText()` 與 `getMetadata().getUsage()`（promptTokens/completionTokens/totalTokens）。fallback 時 usage 全 0、aiEnabled=false。
- 每次呼叫（含 fallback）呼叫 `AiGovernanceService.record(...)`，並把 callId 帶入回應。

### 2.5 DTO
- `Dtos.ChatResponse` 加 `Long callId`（nullable）；建構點更新。Portfolio 同理（或回 callId 於 PortfolioAssessmentResponse）。
- `Dtos.AiFeedbackRequest(decision, note)`、`Dtos.UsageSummaryResponse(...)`。

### 2.6 端點（AiController）
- `POST /api/ai/calls/{id}/feedback`（authenticated）→ 寫 feedback。
- `GET /api/ai/usage`（MANAGER/ADMIN）→ 用量彙總。SecurityConfig：`GET /api/ai/usage` 需 MANAGER 或 ADMIN（`hasAnyRole("MANAGER","ADMIN")`）。

---

## 3. 資料庫變更

Flyway `V6__add_ai_governance.sql`：建 `ai_call_log` 與 `ai_feedback` 兩表（含 FK、索引 customer_id/created_at）。entity 用 JPA 標準映射（無 vector）。

---

## 4. 測試

**單元**：
- `PiiMaskerTest`：email/phone/taxId 遮罩正確、非 PII 不動、null 安全。
**整合（PostgresTestBase）**：
- `AiGovernanceIntegrationTest`：chat 後 `ai_call_log` 有一筆（fallback 模式 tokens=0、ai_enabled=false、masked=true、answer 非空）；`POST feedback` 寫入；`GET /api/ai/usage` 以 MANAGER 回 200、以 SALES 回 403。
- `PiiMaskingIntegrationTest` 或併入上案：確認 grounding 遮罩（可由 PiiMasker 單元 + log masked 旗標間接驗證）。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| Spring AI 2.0 usage metadata API 名稱不確定 | 實作時讀 `ChatResponse`/`Usage` 實際方法；取不到則記 0 並 log |
| 遮罩過度影響 LLM 回答品質 | 只遮 email/phone/taxId（對分析非必要的識別資訊），保留業務語意欄位 |
| ChatResponse 加欄位破壞前端 | callId nullable，前端 TS 多餘欄位不影響；不強制前端改動 |
| fallback 也要記錄 | record() 對 fallback 傳 usage=0/aiEnabled=false，統一記 |

---

## 6. 完成後
- 更新進度；接續 SP5。
