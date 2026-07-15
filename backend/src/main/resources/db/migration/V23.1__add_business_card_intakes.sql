CREATE TABLE business_card_intakes (
    id BIGSERIAL PRIMARY KEY,
    media_id BIGINT NOT NULL UNIQUE REFERENCES temporary_media(id),
    creator_username VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PROCESSING', 'REVIEW_PENDING', 'FAILED', 'CONFIRMED')),
    ocr_model VARCHAR(255) NOT NULL,
    ocr_provider_id BIGINT NOT NULL REFERENCES ai_providers(id),
    recognized_json TEXT,
    duplicate_candidates_json TEXT,
    error_summary VARCHAR(1000),
    confirmed_by VARCHAR(255),
    confirmed_at TIMESTAMPTZ,
    customer_id BIGINT REFERENCES customers(id),
    contact_id BIGINT REFERENCES contacts(id),
    opportunity_id BIGINT REFERENCES opportunities(id),
    task_id BIGINT REFERENCES crm_tasks(id),
    idempotency_key VARCHAR(255),
    idempotency_payload_hash VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(255) NOT NULL DEFAULT 'system',
    CONSTRAINT uq_business_card_creator_idempotency UNIQUE (creator_username, idempotency_key)
);

CREATE INDEX idx_business_card_creator ON business_card_intakes (creator_username, created_at DESC);
CREATE INDEX idx_business_card_status ON business_card_intakes (status, created_at DESC);
