-- 客戶負責業務改為正規關聯：新增 owner_id 外鍵指向 app_users。
-- 先允許 NULL，由啟動時的 SalesTeamInitializer 為現有 owner_name 建立對應 SALES 帳號並回填 owner_id。
-- 保留 owner_name 欄位作為去正規化的顯示快取（寫入時與 owner.displayName 同步），
-- 讓既有以 owner_name 字串彙總的報表（業務排行/下鑽/篩選）維持運作且資料一致。
ALTER TABLE customers ADD COLUMN owner_id BIGINT REFERENCES app_users(id);
CREATE INDEX idx_customers_owner_id ON customers(owner_id);
