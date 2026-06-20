insert into customers (id, name, email, phone, tax_id, industry, owner_name, status, contract_start_date, contract_end_date, renewal_due_date, created_at, updated_at, created_by, updated_by)
values
    (1, '星河製造股份有限公司', 'it@starmill.example', '0911000001', '12345678', '智慧製造', '林宜庭', 'ACTIVE', date '2026-01-01', date '2026-12-31', date '2026-11-30', current_timestamp, current_timestamp, 'seed', 'seed'),
    (2, '海岳物流股份有限公司', 'ops@haiyue.example', '0911000002', '22345678', '物流', '林宜庭', 'ACTIVE', date '2025-04-01', date '2026-04-01', date '2026-03-15', current_timestamp, current_timestamp, 'seed', 'seed'),
    (3, '晨曦醫材有限公司', 'renewal@sunrise.example', '0911000003', '32345678', '醫療器材', '陳柏翰', 'ACTIVE', date '2025-05-01', date '2026-05-01', date '2026-05-20', current_timestamp, current_timestamp, 'seed', 'seed'),
    (4, '空白測試客戶有限公司', 'empty@example.com', '0911000004', '42345678', '測試資料', '陳柏翰', 'ACTIVE', date '2026-01-01', date '2026-12-31', date '2026-10-01', current_timestamp, current_timestamp, 'seed', 'seed');

insert into contacts (customer_id, name, title, email, created_at, updated_at, created_by, updated_by)
values
    (1, '王若晴', '資訊長', 'claire.wang@starmill.example', current_timestamp, current_timestamp, 'seed', 'seed'),
    (2, '張育誠', '營運副總', 'yc.chang@haiyue.example', current_timestamp, current_timestamp, 'seed', 'seed'),
    (3, '李孟蓉', '採購經理', 'maggie.lee@sunrise.example', current_timestamp, current_timestamp, 'seed', 'seed');

insert into interactions (customer_id, type, occurred_at, content, created_at, updated_at, created_by, updated_by)
values
    (1, 'MEETING', timestamp '2026-06-01 10:00:00', '客戶表示今年智慧工廠擴線順利，願意評估增加 30 組 IoT 閘道器授權。', current_timestamp, current_timestamp, 'seed', 'seed'),
    (1, 'EMAIL', timestamp '2026-06-05 15:30:00', '資訊長回覆 PoC 報告正面，要求安排資安與資料保留政策說明。', current_timestamp, current_timestamp, 'seed', 'seed'),
    (1, 'PHONE', timestamp '2026-06-10 09:15:00', '業務確認下週可進入合約條款討論，客戶要求保留年度採購折扣。', current_timestamp, current_timestamp, 'seed', 'seed'),
    (2, 'SUPPORT_TICKET', timestamp '2026-02-20 14:20:00', '客戶反映設備資料同步延遲，客服尚未提供明確修復時程。', current_timestamp, current_timestamp, 'seed', 'seed'),
    (2, 'EMAIL', timestamp '2026-03-02 11:10:00', '營運副總提到預算凍結，且正在比較競品的物流追蹤模組。', current_timestamp, current_timestamp, 'seed', 'seed'),
    (2, 'PHONE', timestamp '2026-03-10 16:00:00', '客戶回覆速度變慢，表示需等內部重新評估供應商名單。', current_timestamp, current_timestamp, 'seed', 'seed'),
    (3, 'EMAIL', timestamp '2026-03-15 10:00:00', '客戶詢問續約報價，但尚未提供明年設備數量預估。', current_timestamp, current_timestamp, 'seed', 'seed'),
    (3, 'PHONE', timestamp '2026-04-01 13:00:00', '業務提醒續約期限將近，客戶表示內部仍在等主管確認。', current_timestamp, current_timestamp, 'seed', 'seed'),
    (3, 'EMAIL', timestamp '2026-04-10 18:00:00', '最後一次郵件未收到回覆，續約商機仍停在資格評估階段。', current_timestamp, current_timestamp, 'seed', 'seed');

insert into opportunities (customer_id, name, stage, amount, expected_close_date, type, created_at, updated_at, created_by, updated_by)
values
    (1, '智慧工廠擴充授權', 'NEGOTIATION', 1500000.00, date '2026-07-15', 'NEW_BUSINESS', current_timestamp, current_timestamp, 'seed', 'seed'),
    (2, '物流追蹤平台續約', 'PROPOSAL', 800000.00, date '2026-04-15', 'RENEWAL', current_timestamp, current_timestamp, 'seed', 'seed'),
    (3, '醫材雲端維護續約', 'QUALIFICATION', 520000.00, date '2026-05-25', 'RENEWAL', current_timestamp, current_timestamp, 'seed', 'seed');

insert into knowledge_documents (doc_type, title, content, similarity_hint, created_at, updated_at, created_by, updated_by)
values
    ('PRODUCT', '智慧手機巡檢方案', '智慧手機巡檢方案適合需要外勤巡檢、即時拍照回傳與設備狀態追蹤的團隊，標準保固為一年，可加購延伸保固。', 0.93, current_timestamp, current_timestamp, 'seed', 'seed'),
    ('POLICY', '企業服務條款', '年度訂閱合約含 8x5 支援與重大故障 4 小時內回應。若客戶需 24x7 支援，需升級為企業維運方案。', 0.88, current_timestamp, current_timestamp, 'seed', 'seed'),
    ('PLAYBOOK', '續約風險話術', '若客戶近期互動減少且提及預算凍結，業務應先安排主管關懷會議，再提出分階段續約選項。', 0.91, current_timestamp, current_timestamp, 'seed', 'seed');

