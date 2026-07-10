-- SP12：知識庫 chunk 級 embedding（文件切段後向量檢索）
create table if not exists knowledge_chunks (
    id              bigserial primary key,
    document_id     bigint not null references knowledge_documents (id) on delete cascade,
    chunk_index     int not null,
    content         text not null,
    embedding       vector(1024),
    constraint uq_knowledge_chunks_doc_idx unique (document_id, chunk_index)
);

create index if not exists idx_knowledge_chunks_document on knowledge_chunks (document_id);
create index if not exists idx_knowledge_chunks_embedding
    on knowledge_chunks using hnsw (embedding vector_cosine_ops);
