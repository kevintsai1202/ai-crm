-- 建立 AI provider 設定表
CREATE TABLE ai_providers (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(64) NOT NULL UNIQUE,
    base_url    TEXT,
    api_key     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  VARCHAR(64)
);

-- 新增 current provider ID 設定鍵（空字串 = 未指定 provider）
INSERT INTO system_settings (setting_key, setting_value, updated_at, updated_by)
VALUES ('ai.chat.provider_id', '', now(), null)
ON CONFLICT (setting_key) DO NOTHING;

-- 將 model_options 從純字串陣列遷移為物件陣列（providerId: null 待用戶指定）
-- 只在現有值為有效 JSON 陣列時執行；空值或非陣列跳過
UPDATE system_settings
SET setting_value = (
    SELECT COALESCE(
        (
            SELECT jsonb_agg(
                jsonb_build_object('model', item.value, 'providerId', null)
            )::text
            FROM jsonb_array_elements_text(setting_value::jsonb) AS item(value)
        ),
        '[]'
    )
)
WHERE setting_key = 'ai.chat.model_options'
  AND setting_value IS NOT NULL
  AND setting_value ~ '^\s*\[';
