-- 帳號啟用旗標：支援管理員停用/啟用帳號（停用者無法登入，但保留歷史資料與關聯）
ALTER TABLE app_users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
