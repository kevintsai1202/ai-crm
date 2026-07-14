CREATE TABLE temporary_media (
    id BIGSERIAL PRIMARY KEY,
    object_key VARCHAR(255) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256 VARCHAR(64) NOT NULL,
    purpose VARCHAR(32) NOT NULL CHECK (purpose IN ('BUSINESS_CARD', 'MEETING_AUDIO')),
    status VARCHAR(32) NOT NULL CHECK (status IN ('UPLOADED', 'PROCESSING', 'REVIEW_PENDING', 'CONFIRMED', 'FAILED', 'DELETED')),
    creator_username VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    error_summary VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(255) NOT NULL DEFAULT 'system'
);

CREATE INDEX idx_temporary_media_cleanup ON temporary_media (status, expires_at);
CREATE INDEX idx_temporary_media_creator ON temporary_media (creator_username, created_at DESC);
