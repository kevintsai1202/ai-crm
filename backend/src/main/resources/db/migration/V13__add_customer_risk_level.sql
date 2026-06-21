-- 風險等級落地為 DB 欄位，讓清單風險篩選改走資料庫分頁（取代記憶體分頁）。
alter table customers add column risk_level varchar(10);
alter table customers add column risk_computed_at timestamp;

-- 風險篩選與既有缺失條件的索引。
create index if not exists idx_customers_risk_level on customers(risk_level);
create index if not exists idx_customers_status on customers(status);
create index if not exists idx_customers_renewal_due_date on customers(renewal_due_date);
