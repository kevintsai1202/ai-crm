-- V24：AI 電話／會議 Copilot session。保存媒體、客戶／商機、轉錄模型、transcript、
-- AI 摘要、結構化草稿、狀態與確認 audit。transcript 於確認後保留作為正式互動依據。
CREATE TABLE meeting_copilot_sessions (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    media_id BIGINT NOT NULL UNIQUE REFERENCES temporary_media(id),
    creator_username VARCHAR(255) NOT NULL,
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    opportunity_id BIGINT REFERENCES opportunities(id),
    status VARCHAR(32) NOT NULL CHECK (status IN ('UPLOADED', 'PROCESSING', 'REVIEW_PENDING', 'FAILED', 'CONFIRMED')),
    transcription_model VARCHAR(255) NOT NULL,
    transcription_provider_id BIGINT NOT NULL REFERENCES ai_providers(id),
    transcript TEXT,
    summary TEXT,
    draft_json TEXT,
    error_summary VARCHAR(1000),
    confirmed_by VARCHAR(255),
    confirmed_at TIMESTAMPTZ,
    idempotency_key VARCHAR(255),
    idempotency_payload_hash VARCHAR(64),
    confirm_result_json TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(255) NOT NULL DEFAULT 'system',
    -- 同一使用者的冪等鍵唯一，NULL 值允許多筆（未確認 session）。
    CONSTRAINT uq_meeting_copilot_creator_idempotency UNIQUE (creator_username, idempotency_key)
);

CREATE INDEX idx_meeting_copilot_creator ON meeting_copilot_sessions (creator_username, created_at DESC);
CREATE INDEX idx_meeting_copilot_status ON meeting_copilot_sessions (status, created_at DESC);
