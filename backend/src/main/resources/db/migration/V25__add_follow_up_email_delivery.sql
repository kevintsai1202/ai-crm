-- V25：AI 跟進信與 Zeabur Sendmail 寄送。
-- follow_up_drafts 保存 AI/人工草稿版本鏈（parent_id + version_number），含 grounding 引用依據與核准者；
-- outbound_emails 保存核准當下的內容快照、寄送狀態、Zeabur message id、重試次數與去敏錯誤。
-- 憑證絕不寫入任一表。customer_id/opportunity_id 為正式 FK，DemoDataService.reset() 會先清這兩表。

CREATE TABLE follow_up_drafts (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    opportunity_id BIGINT REFERENCES opportunities(id),
    creator_username VARCHAR(255) NOT NULL,
    version_number INTEGER NOT NULL,
    -- 版本鏈：人工修改形成新版本並指向上一版；第一版為 NULL。
    parent_id BIGINT REFERENCES follow_up_drafts(id),
    model VARCHAR(255),
    ai_provider_id BIGINT,
    grounding TEXT,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    edited BOOLEAN NOT NULL DEFAULT FALSE,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(255) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_follow_up_drafts_customer ON follow_up_drafts (customer_id, created_at DESC);
CREATE INDEX idx_follow_up_drafts_creator ON follow_up_drafts (creator_username, created_at DESC);

CREATE TABLE outbound_emails (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    draft_id BIGINT NOT NULL REFERENCES follow_up_drafts(id),
    creator_username VARCHAR(255) NOT NULL,
    from_address VARCHAR(255) NOT NULL,
    reply_to VARCHAR(255) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('QUEUED', 'SENT', 'FAILED')),
    message_id VARCHAR(255),
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_summary VARCHAR(1000),
    idempotency_key VARCHAR(255),
    idempotency_payload_hash VARCHAR(64),
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(255) NOT NULL DEFAULT 'system',
    -- 同一使用者的寄送冪等鍵唯一，NULL 允許多筆。
    CONSTRAINT uq_outbound_email_creator_idempotency UNIQUE (creator_username, idempotency_key)
);

CREATE INDEX idx_outbound_emails_status ON outbound_emails (status, created_at DESC);
CREATE INDEX idx_outbound_emails_draft ON outbound_emails (draft_id);
