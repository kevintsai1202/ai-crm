-- V18__enrich_opportunity_sales_model.sql
-- SP8：商機資料模型強化（owner / leadSource / probability / closeReason + actualCloseDate）

-- ① 新增欄位（lead_source 以 DEFAULT 完成回填）
ALTER TABLE opportunities ADD COLUMN owner_id          BIGINT;
ALTER TABLE opportunities ADD COLUMN owner_name        VARCHAR(255);
ALTER TABLE opportunities ADD COLUMN lead_source       VARCHAR(16) NOT NULL DEFAULT 'OUTBOUND';
ALTER TABLE opportunities ADD COLUMN probability       INT;
ALTER TABLE opportunities ADD COLUMN close_reason      VARCHAR(32);
ALTER TABLE opportunities ADD COLUMN close_reason_note TEXT;
ALTER TABLE opportunities ADD COLUMN actual_close_date DATE;

ALTER TABLE opportunities
  ADD CONSTRAINT fk_opportunities_owner FOREIGN KEY (owner_id) REFERENCES app_users(id);
CREATE INDEX idx_opportunities_owner       ON opportunities(owner_id);
CREATE INDEX idx_opportunities_lead_source ON opportunities(lead_source);

-- ② 回填 owner_id / owner_name ← 客戶現有負責業務（績效口徑改商機 owner 的前提；保證切換零差異）
UPDATE opportunities o
SET owner_id = (SELECT c.owner_id FROM customers c WHERE c.id = o.customer_id)
WHERE o.owner_id IS NULL;

UPDATE opportunities o
SET owner_name = (SELECT c.owner_name FROM customers c WHERE c.id = o.customer_id)
WHERE o.owner_name IS NULL;

-- ③ 回填 probability ← 依階段預設機率（與後端對照表一致）
UPDATE opportunities SET probability = CASE stage
    WHEN 'QUALIFICATION' THEN 20
    WHEN 'PROPOSAL'      THEN 50
    WHEN 'NEGOTIATION'   THEN 75
    WHEN 'CLOSED_WON'    THEN 100
    WHEN 'CLOSED_LOST'   THEN 0
END
WHERE probability IS NULL;

-- ④ 回填 actual_close_date ← 已結案者用預計成交日（缺則今天）
UPDATE opportunities
SET actual_close_date = COALESCE(expected_close_date, CURRENT_DATE)
WHERE stage IN ('CLOSED_WON', 'CLOSED_LOST') AND actual_close_date IS NULL;

-- ⑤ close_reason 刻意不回填：歷史案子無真實輸贏原因，留 NULL（報表歸「未填」）。
