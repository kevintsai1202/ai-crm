-- V26：商機健康度與下一最佳行動 snapshot。
-- opportunity_health_snapshots 保存每次重算的總分、各分項（含分數/可解釋 reason/evidence，序列化為 JSON）、
-- 下一最佳行動、規則版本與（選配）AI 模型、計算時間。歷史 snapshot 保留（同商機多筆）以呈現趨勢。
-- 本功能只讀商機/互動/情緒/任務/聯絡人訊號後另存 snapshot，絕不修改 opportunities 的 stage 或 probability。
-- opportunity_id 為正式 FK；DemoDataService.clearBusinessData() 會於刪商機前先清本表。

CREATE TABLE opportunity_health_snapshots (
    id BIGSERIAL PRIMARY KEY,
    opportunity_id BIGINT NOT NULL REFERENCES opportunities(id),
    -- 總分（0–100，恆等於各分項分數總和）。
    total_score INTEGER NOT NULL,
    -- 各分項明細（key/label/score/maxScore/reason/evidence 陣列），以 JSON 文字保存供趨勢與解釋呈現。
    components TEXT NOT NULL,
    -- 下一最佳行動文案（deterministic 產生；AI 為選配，失敗回退 deterministic）。
    next_best_action TEXT,
    -- 規則版本，供歷史 snapshot 追溯評分規則。
    rule_version VARCHAR(32) NOT NULL,
    -- 產生下一最佳行動的 AI 模型；deterministic 產生時為 NULL。
    model VARCHAR(255),
    -- 計算時間（趨勢排序用）。
    calculated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(255) NOT NULL DEFAULT 'system'
);

-- 取某商機最新 snapshot 與歷史趨勢；calculated_at 相同時以 id 為次序，確保排序穩定。
CREATE INDEX idx_opp_health_opportunity ON opportunity_health_snapshots (opportunity_id, calculated_at DESC, id DESC);
