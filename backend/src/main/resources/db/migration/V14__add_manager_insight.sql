-- 模組 C：Manager AI 分析快取表。scope=TEAM 時 owner_name 為 null；scope=OWNER 時存業務顯示名稱。
CREATE TABLE manager_insight (
    id           BIGSERIAL PRIMARY KEY,
    scope        VARCHAR(16)  NOT NULL,          -- TEAM | OWNER
    owner_name   VARCHAR(255),                   -- OWNER 時的業務名；TEAM 為 null
    content      TEXT         NOT NULL,          -- 產出的 Markdown 報告
    model        VARCHAR(128),                   -- 模型名；fallback 時為 null
    generated_at TIMESTAMP    NOT NULL
);

-- 查快取用：TEAM 走 scope；OWNER 走 (scope, owner_name)
CREATE INDEX idx_manager_insight_scope ON manager_insight (scope);
CREATE INDEX idx_manager_insight_scope_owner ON manager_insight (scope, owner_name);
