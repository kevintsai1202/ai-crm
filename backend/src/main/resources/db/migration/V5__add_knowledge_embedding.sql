-- 啟用 pgvector，為知識文件加 1024 維 embedding 欄位與 HNSW cosine 索引
create extension if not exists vector;
alter table knowledge_documents add column embedding vector(1024);
create index idx_knowledge_embedding on knowledge_documents using hnsw (embedding vector_cosine_ops);
