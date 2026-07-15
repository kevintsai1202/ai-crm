ALTER TABLE business_card_intakes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE business_card_intakes ADD CONSTRAINT ck_business_card_confirmation_fields CHECK (
    (status = 'CONFIRMED' AND confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL
        AND customer_id IS NOT NULL AND contact_id IS NOT NULL AND opportunity_id IS NOT NULL AND task_id IS NOT NULL
        AND idempotency_key IS NOT NULL AND idempotency_payload_hash IS NOT NULL)
    OR
    (status <> 'CONFIRMED' AND confirmed_at IS NULL AND confirmed_by IS NULL
        AND customer_id IS NULL AND contact_id IS NULL AND opportunity_id IS NULL AND task_id IS NULL
        AND idempotency_key IS NULL AND idempotency_payload_hash IS NULL)
);

ALTER TABLE temporary_media DROP CONSTRAINT IF EXISTS temporary_media_status_check;
ALTER TABLE temporary_media ADD CONSTRAINT temporary_media_status_check CHECK (
    status IN ('UPLOADED','PROCESSING','REVIEW_PENDING','CONFIRMED','DELETE_PENDING','FAILED','DELETED')
);
