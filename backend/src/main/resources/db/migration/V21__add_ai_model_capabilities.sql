-- 新增 OCR 與語音轉錄用途的模型 assignment；空字串代表尚未設定。
INSERT INTO system_settings (setting_key, setting_value, updated_at, updated_by) VALUES
    ('ai.ocr.model', '', now(), null),
    ('ai.ocr.provider_id', '', now(), null),
    ('ai.transcription.model', '', now(), null),
    ('ai.transcription.provider_id', '', now(), null)
ON CONFLICT (setting_key) DO NOTHING;

-- 舊模型選項沒有可靠的 modality metadata，統一補為 UNKNOWN，絕不依模型名稱猜測。
UPDATE system_settings
SET setting_value = (
    SELECT COALESCE(
        jsonb_agg(
            CASE
                WHEN jsonb_typeof(option_value) = 'object' THEN
                    option_value
                    || jsonb_build_object(
                        'capabilities', COALESCE(option_value->'capabilities', '[]'::jsonb),
                        'capabilitySource', COALESCE(option_value->'capabilitySource', '"UNKNOWN"'::jsonb)
                    )
                ELSE option_value
            END
            ORDER BY ordinal
        ),
        '[]'::jsonb
    )::text
    FROM jsonb_array_elements(setting_value::jsonb) WITH ORDINALITY AS item(option_value, ordinal)
)
WHERE setting_key = 'ai.chat.model_options'
  AND setting_value IS NOT NULL
  AND pg_input_is_valid(setting_value, 'jsonb')
  AND jsonb_typeof(setting_value::jsonb) = 'array';
