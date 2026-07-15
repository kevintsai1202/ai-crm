-- V27：Stakeholder 決策鏈與關係圖。
-- stakeholder_roles：綁定單一 Contact，保存決策角色、影響力、立場、信心、來源(AI/MANUAL)與確認狀態。
-- stakeholder_relations：綁定同一客戶的兩位 Contact，保存關係類型、來源與確認狀態。
-- AI 建議(SUGGESTED)與人工確認事實(CONFIRMED)以 status 區分；REJECTED 保留 audit 但不顯示為事實。
-- contact_id / from_contact_id / to_contact_id 皆為正式 FK 參照 contacts(id)，刻意不使用 ON DELETE CASCADE；
-- DemoDataService.clearBusinessData() 會於刪 contacts 前先清本兩表，避免 FK 阻擋既有 reset 流程。

CREATE TABLE stakeholder_roles (
    id BIGSERIAL PRIMARY KEY,
    -- 綁定的聯絡人（決策角色掛在單一 Contact）。
    contact_id BIGINT NOT NULL REFERENCES contacts(id),
    -- 決策角色類型。
    role_type VARCHAR(32) NOT NULL,
    -- 影響力程度。
    influence VARCHAR(16) NOT NULL,
    -- 對我方方案的立場。
    stance VARCHAR(16) NOT NULL,
    -- 信心分數（0–100）。
    confidence INTEGER NOT NULL,
    -- 資料來源（AI 推測 / 人工輸入）。
    source VARCHAR(16) NOT NULL,
    -- 確認狀態（SUGGESTED / CONFIRMED / REJECTED）。
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(255) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_stakeholder_roles_role_type CHECK (role_type IN
        ('DECISION_MAKER','ECONOMIC_BUYER','TECHNICAL_BUYER','CHAMPION','INFLUENCER','GATEKEEPER','END_USER','UNKNOWN')),
    CONSTRAINT chk_stakeholder_roles_influence CHECK (influence IN ('HIGH','MEDIUM','LOW')),
    CONSTRAINT chk_stakeholder_roles_stance CHECK (stance IN ('SUPPORTER','NEUTRAL','DETRACTOR')),
    CONSTRAINT chk_stakeholder_roles_source CHECK (source IN ('AI','MANUAL')),
    CONSTRAINT chk_stakeholder_roles_status CHECK (status IN ('SUGGESTED','CONFIRMED','REJECTED')),
    CONSTRAINT chk_stakeholder_roles_confidence CHECK (confidence BETWEEN 0 AND 100)
);

CREATE INDEX idx_stakeholder_roles_contact ON stakeholder_roles(contact_id);
CREATE INDEX idx_stakeholder_roles_status ON stakeholder_roles(status);

CREATE TABLE stakeholder_relations (
    id BIGSERIAL PRIMARY KEY,
    -- 關係起點聯絡人。
    from_contact_id BIGINT NOT NULL REFERENCES contacts(id),
    -- 關係終點聯絡人。
    to_contact_id BIGINT NOT NULL REFERENCES contacts(id),
    -- 關係類型。
    relation_type VARCHAR(24) NOT NULL,
    -- 資料來源（AI 推測 / 人工輸入）。
    source VARCHAR(16) NOT NULL,
    -- 確認狀態。
    status VARCHAR(16) NOT NULL,
    -- 信心分數（0–100）。
    confidence INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(255) NOT NULL DEFAULT 'system',
    CONSTRAINT chk_stakeholder_relations_type CHECK (relation_type IN
        ('REPORTS_TO','PEER','INFLUENCES','ALLY','RIVAL')),
    CONSTRAINT chk_stakeholder_relations_source CHECK (source IN ('AI','MANUAL')),
    CONSTRAINT chk_stakeholder_relations_status CHECK (status IN ('SUGGESTED','CONFIRMED','REJECTED')),
    CONSTRAINT chk_stakeholder_relations_confidence CHECK (confidence BETWEEN 0 AND 100),
    -- 關係兩端不可為同一位聯絡人（跨 customer 由應用層另行拒絕）。
    CONSTRAINT chk_stakeholder_relations_distinct CHECK (from_contact_id <> to_contact_id)
);

CREATE INDEX idx_stakeholder_relations_from ON stakeholder_relations(from_contact_id);
CREATE INDEX idx_stakeholder_relations_to ON stakeholder_relations(to_contact_id);
CREATE INDEX idx_stakeholder_relations_status ON stakeholder_relations(status);
