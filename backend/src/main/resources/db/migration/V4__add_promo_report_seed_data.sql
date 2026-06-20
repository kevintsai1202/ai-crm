insert into customers (id, name, email, phone, tax_id, industry, owner_name, status, contract_start_date, contract_end_date, renewal_due_date, created_at, updated_at, created_by, updated_by)
values
    (10, '北辰雲端科技股份有限公司', 'admin@northcloud.example', '0912000010', '52345610', '雲端服務', '林宜庭', 'ACTIVE', date '2026-01-01', date '2026-12-31', date '2026-10-15', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (11, '遠航智慧物流有限公司', 'ops@voyage.example', '0912000011', '52345611', '物流', '林宜庭', 'ACTIVE', date '2026-02-01', date '2027-01-31', date '2026-12-10', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (12, '禾康醫療雲股份有限公司', 'it@hecare.example', '0912000012', '52345612', '醫療科技', '陳柏翰', 'ACTIVE', date '2025-12-01', date '2026-11-30', date '2026-09-25', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (13, '宏鼎金融數據股份有限公司', 'data@grandfin.example', '0912000013', '52345613', '金融科技', '張雅筑', 'ACTIVE', date '2026-03-01', date '2027-02-28', date '2026-12-20', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (14, '青禾零售通路有限公司', 'buy@greenretail.example', '0912000014', '52345614', '零售通路', '張雅筑', 'ACTIVE', date '2025-08-01', date '2026-07-31', date '2026-07-15', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (15, '睿能半導體股份有限公司', 'factory@ruineng.example', '0912000015', '52345615', '智慧製造', '王昱翔', 'ACTIVE', date '2026-01-15', date '2027-01-14', date '2026-11-01', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (16, '佳境營建科技有限公司', 'pm@buildtech.example', '0912000016', '52345616', '營建科技', '王昱翔', 'ACTIVE', date '2025-11-01', date '2026-10-31', date '2026-08-30', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (17, '辰光教育平台股份有限公司', 'ops@eduspark.example', '0912000017', '52345617', '教育科技', '林宜庭', 'ACTIVE', date '2026-04-01', date '2027-03-31', date '2027-01-20', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (18, '鼎盛能源管理有限公司', 'energy@dingsun.example', '0912000018', '52345618', '能源管理', '陳柏翰', 'ACTIVE', date '2025-09-01', date '2026-08-31', date '2026-06-20', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (19, '映海旅宿科技股份有限公司', 'hotel@oceanstay.example', '0912000019', '52345619', '旅宿科技', '張雅筑', 'ACTIVE', date '2026-02-15', date '2027-02-14', date '2026-12-05', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (20, '捷迅電商有限公司', 'growth@fastshop.example', '0912000020', '52345620', '零售通路', '王昱翔', 'ACTIVE', date '2026-05-01', date '2027-04-30', date '2027-02-15', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (21, '安信保險科技股份有限公司', 'risk@trustins.example', '0912000021', '52345621', '金融科技', '林宜庭', 'ACTIVE', date '2025-07-01', date '2026-06-30', date '2026-05-30', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (22, '柏林生技有限公司', 'lab@biolin.example', '0912000022', '52345622', '醫療科技', '陳柏翰', 'ACTIVE', date '2026-03-15', date '2027-03-14', date '2027-01-10', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (23, '瑞田農業物聯網股份有限公司', 'iot@agritech.example', '0912000023', '52345623', '物聯網', '王昱翔', 'ACTIVE', date '2025-10-01', date '2026-09-30', date '2026-08-15', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (24, '聯捷冷鏈物流有限公司', 'cold@linkcold.example', '0912000024', '52345624', '物流', '張雅筑', 'INACTIVE', date '2025-05-01', date '2026-04-30', date '2026-04-10', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (25, '精準客服雲股份有限公司', 'cx@precisecx.example', '0912000025', '52345625', '雲端服務', '陳柏翰', 'ACTIVE', date '2026-06-01', date '2027-05-31', date '2027-03-25', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (26, '永續碳盤查科技有限公司', 'carbon@sustain.example', '0912000026', '52345626', '能源管理', '林宜庭', 'ACTIVE', date '2025-12-15', date '2026-12-14', date '2026-10-30', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (27, '智造研發中心股份有限公司', 'rd@makerlab.example', '0912000027', '52345627', '智慧製造', '王昱翔', 'ACTIVE', date '2026-01-20', date '2027-01-19', date '2026-11-18', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (28, '華信資安顧問有限公司', 'sec@huatrust.example', '0912000028', '52345628', '資安服務', '張雅筑', 'LEVERAGED', date '2025-06-01', date '2026-05-31', date '2026-05-01', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (29, '新曜媒體科技股份有限公司', 'media@newlight.example', '0912000029', '52345629', '媒體科技', '林宜庭', 'ACTIVE', date '2026-04-15', date '2027-04-14', date '2027-02-01', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4');

insert into contacts (customer_id, name, title, email, created_at, updated_at, created_by, updated_by)
values
    (10, '許家豪', '技術長', 'hao.hsu@northcloud.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (11, '吳佩珊', '營運長', 'peishan.wu@voyage.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (12, '黃怡君', '資訊經理', 'yijun.huang@hecare.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (13, '周建宏', '資料長', 'jason.chou@grandfin.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (14, '林佳蓉', '採購主管', 'joyce.lin@greenretail.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (15, '蔡明哲', '廠務經理', 'ming.tsai@ruineng.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (16, '徐雅雯', '專案辦公室主任', 'amy.hsu@buildtech.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (17, '陳冠宇', '產品總監', 'kuanyu.chen@eduspark.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (18, '郭品妤', '能源管理師', 'pin.kuo@dingsun.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (19, '鄭宇軒', '數位營運主管', 'yuxuan.cheng@oceanstay.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (20, '賴欣怡', '成長經理', 'sinyi.lai@fastshop.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (21, '何志偉', '風控主管', 'zhiwei.ho@trustins.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (22, '羅美玲', '實驗室主任', 'mei.luo@biolin.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (23, '楊承翰', '農業數據主管', 'cheng.yang@agritech.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (24, '莊雅婷', '供應鏈經理', 'yating.chuang@linkcold.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (25, '邱子庭', '客服營運總監', 'tina.chiu@precisecx.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (26, '謝孟潔', '永續長', 'maggie.hsieh@sustain.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (27, '劉品辰', '研發副理', 'pinchen.liu@makerlab.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (28, '沈柏翰', '資安顧問', 'brian.shen@huatrust.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (29, '潘若瑜', '媒體策略總監', 'zoe.pan@newlight.example', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4');

insert into interactions (customer_id, type, occurred_at, content, created_at, updated_at, created_by, updated_by)
values
    (10, 'MEETING', timestamp '2026-06-12 10:00:00', '客戶確認雲端治理專案預算，要求下週提供正式報價。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (10, 'EMAIL', timestamp '2026-06-16 14:30:00', '技術長回覆架構圖可行，等待法務審合約條款。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (11, 'PHONE', timestamp '2026-06-08 09:20:00', '營運長表示旺季前必須完成物流追蹤看板上線。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (11, 'MEETING', timestamp '2026-06-18 11:00:00', '雙方確認導入時程，客戶要求加入 SLA 監控報表。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (12, 'EMAIL', timestamp '2026-05-21 17:30:00', '資訊經理要求補充醫療資料保留與權限稽核說明。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (12, 'SUPPORT_TICKET', timestamp '2026-06-03 15:20:00', '測試環境匯入速度偏慢，客服已安排效能調校。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (13, 'MEETING', timestamp '2026-06-14 13:30:00', '資料長認可風控儀表板，進入採購議價。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (14, 'EMAIL', timestamp '2026-04-18 16:45:00', '採購主管提到預算凍結，需要延後到第三季再決策。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (15, 'MEETING', timestamp '2026-06-11 10:45:00', '廠務經理要求把機台稼動率串進 CRM 報表。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (16, 'PHONE', timestamp '2026-05-06 09:40:00', '專案辦公室仍在評估競品，要求延長 PoC。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (17, 'EMAIL', timestamp '2026-06-17 12:15:00', '產品總監確認新增授權數量，願意進入合約草稿。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (18, 'SUPPORT_TICKET', timestamp '2026-03-22 15:10:00', '能源監控資料偶發延遲，客戶要求主管說明改善時程。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (19, 'MEETING', timestamp '2026-06-07 16:00:00', '旅宿業旺季需求增加，客戶要加購即時客服模組。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (20, 'EMAIL', timestamp '2026-06-13 11:20:00', '成長經理確認電商會員分群功能納入採購清單。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (21, 'PHONE', timestamp '2026-04-02 10:30:00', '風控主管表示續約仍未收到回覆，內部正在比較競品。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (22, 'MEETING', timestamp '2026-06-10 14:00:00', '實驗室主任同意擴充設備連線數，等待採購單。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (23, 'EMAIL', timestamp '2026-05-29 09:10:00', '農業數據主管要求補充離線同步方案。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (24, 'SUPPORT_TICKET', timestamp '2026-03-08 15:00:00', '客戶反映冷鏈告警不穩定且要求退款討論。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (25, 'MEETING', timestamp '2026-06-19 10:10:00', '客服營運總監確認導入 AI 分流，需主管核准採購。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (26, 'EMAIL', timestamp '2026-06-04 13:35:00', '永續長確認碳盤查報表格式，要求加入董事會摘要。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (27, 'MEETING', timestamp '2026-06-15 15:30:00', '研發副理確認二期整合範圍，要求保留年度折扣。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (28, 'PHONE', timestamp '2026-02-24 10:20:00', '資安顧問表示採購延遲，需等待年度預算釋出。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (29, 'EMAIL', timestamp '2026-06-09 17:10:00', '媒體策略總監要求本週提供媒體投放 ROI 報表樣張。', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4');

insert into opportunities (customer_id, name, stage, amount, expected_close_date, type, created_at, updated_at, created_by, updated_by)
values
    (10, '雲端治理年度訂閱', 'NEGOTIATION', 2100000.00, date '2026-07-20', 'NEW_BUSINESS', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (10, 'FinOps 顧問服務續約', 'PROPOSAL', 680000.00, date '2026-09-15', 'RENEWAL', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (11, '物流追蹤 SLA 看板', 'PROPOSAL', 1250000.00, date '2026-08-10', 'NEW_BUSINESS', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (12, '醫療資料稽核模組', 'QUALIFICATION', 920000.00, date '2026-09-30', 'NEW_BUSINESS', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (13, '風控即時儀表板', 'NEGOTIATION', 3200000.00, date '2026-07-28', 'NEW_BUSINESS', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (14, '門市會員整合續約', 'CLOSED_LOST', 540000.00, date '2026-06-30', 'RENEWAL', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (15, '半導體機台稼動率整合', 'PROPOSAL', 2800000.00, date '2026-08-22', 'NEW_BUSINESS', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (16, '營建專案協同平台', 'QUALIFICATION', 760000.00, date '2026-10-18', 'NEW_BUSINESS', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (17, '教育平台授權擴充', 'NEGOTIATION', 1450000.00, date '2026-07-08', 'NEW_BUSINESS', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (18, '能源監控續約挽回', 'PROPOSAL', 880000.00, date '2026-06-25', 'RENEWAL', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (19, '旅宿客服模組加購', 'NEGOTIATION', 1180000.00, date '2026-08-05', 'NEW_BUSINESS', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (20, '電商會員分群導入', 'PROPOSAL', 990000.00, date '2026-09-05', 'NEW_BUSINESS', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (21, '保險風控續約', 'CLOSED_LOST', 1350000.00, date '2026-05-30', 'RENEWAL', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (22, '生技設備連線擴充', 'NEGOTIATION', 1680000.00, date '2026-08-30', 'NEW_BUSINESS', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (23, '農業物聯網離線同步', 'QUALIFICATION', 720000.00, date '2026-11-12', 'NEW_BUSINESS', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (24, '冷鏈告警續約', 'CLOSED_LOST', 610000.00, date '2026-04-15', 'RENEWAL', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (25, 'AI 客服分流平台', 'PROPOSAL', 1880000.00, date '2026-10-01', 'NEW_BUSINESS', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (26, '碳盤查董事會報表', 'NEGOTIATION', 1320000.00, date '2026-09-18', 'NEW_BUSINESS', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (27, '研發數據中台二期', 'PROPOSAL', 2450000.00, date '2026-11-20', 'NEW_BUSINESS', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (28, '資安顧問服務續約', 'QUALIFICATION', 480000.00, date '2026-07-01', 'RENEWAL', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (29, '媒體投放 ROI 報表', 'NEGOTIATION', 960000.00, date '2026-08-18', 'NEW_BUSINESS', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4'),
    (29, '廣告數據維運續約', 'CLOSED_WON', 520000.00, date '2026-06-12', 'RENEWAL', current_timestamp, current_timestamp, 'seed-v4', 'seed-v4');

alter table customers alter column id restart with 200;
alter table contacts alter column id restart with 200;
alter table interactions alter column id restart with 200;
alter table opportunities alter column id restart with 200;
alter table knowledge_documents alter column id restart with 200;
