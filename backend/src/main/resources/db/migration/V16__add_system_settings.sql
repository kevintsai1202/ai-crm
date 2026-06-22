-- 通用系統設定（全域 key-value）。本次用於 AI 對話模型設定：
--   ai.chat.model          目前選用模型名；空字串代表「使用環境變數預設」
--   ai.chat.model_options  可選模型清單（JSON 陣列字串），供前端下拉
create table system_settings (
    setting_key   varchar(64) primary key,
    setting_value text not null,
    updated_at    timestamp with time zone not null,
    updated_by    varchar(64)
);

-- 種子：目前模型留空（走環境變數），預設候選清單兩筆
insert into system_settings (setting_key, setting_value, updated_at, updated_by) values
    ('ai.chat.model', '', now(), null),
    ('ai.chat.model_options', '["gemini-3.1-flash-lite-preview","gpt-4o-mini"]', now(), null);
