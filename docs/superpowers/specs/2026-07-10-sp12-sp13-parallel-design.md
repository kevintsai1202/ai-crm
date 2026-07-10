# SP12 + SP13 並行設計定案（2026-07-10）

## SP12 — RAG Chunking + 引用服務抽出

**目標：** chunk 級向量檢索；`loadCitations` 抽到 `RagCitationService`，InsightService 瘦身一階（完整再拆 Llm 層留 follow-up）。

**Chunking：**
- 以字元切：目標 600、overlap 80；短文 < 100 字仍 1 chunk
- 表 `knowledge_chunks(id, document_id, chunk_index, content, embedding vector(1024))`
- 啟動索引：無 chunk 的文件 → 切段 + embed
- `reindexAll`：清 chunk 重算 + 仍更新文件級 embedding（相容舊測試）
- 檢索：chunk top-3 join 文件 title/doc_type；空則文件級；再空 similarityHint

## SP13 — StageHistory + 漏斗停留

**超時門檻（天）：** QUALIFICATION=14, PROPOSAL=14, NEGOTIATION=21；CLOSED_* 不計超時  
**回填：** migration 為每筆商機插一筆 `from_stage=null, to_stage=current, changed_at=created_at`  
**寫入點：** 建立商機、updateStage/closeWith 且階段變更時  
**API：** `StageReport` 加 `avgDaysInStage`（Double）、`overdueCount`（long）；前端漏斗副標顯示  

**統計：** 自啟用日起精準；回填用 created_at 近似「進入當前階段」時間  
