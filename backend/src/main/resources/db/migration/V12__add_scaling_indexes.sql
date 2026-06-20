-- 規模化索引補強:為上萬筆資料量補上仍缺的索引。
-- 現況盤點後,多數 FK(interactions/opportunities/interaction_insights/chat_messages/ai_call_log
-- 的 customer_id、owner_id 等)V1~V11 已建索引;此處只補真正仍缺的兩處。
-- 用 IF NOT EXISTS 確保冪等(就算某環境已手動補過也不報錯)。

-- contacts.customer_id 是唯一沒有索引的外鍵:載入客戶詳情/批次抓聯絡人時避免全表掃描。
CREATE INDEX IF NOT EXISTS idx_contacts_customer ON contacts(customer_id);

-- 客戶列表常以 industry 等值篩選(buildSpec),上萬筆時加索引避免 seq scan。
CREATE INDEX IF NOT EXISTS idx_customers_industry ON customers(industry);
