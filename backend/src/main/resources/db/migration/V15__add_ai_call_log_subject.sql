-- AI 功能一致化：ai_call_log 加 subject 維度。
-- OWNER_COACHING 存 ownerName；TEAM_ANALYSIS / PORTFOLIO / 客戶呼叫皆為 null（客戶用 customer_id）。
ALTER TABLE ai_call_log ADD COLUMN subject VARCHAR(255);

-- 依類型 + subject 查歷程
CREATE INDEX idx_ai_call_log_calltype_subject ON ai_call_log (call_type, subject);
