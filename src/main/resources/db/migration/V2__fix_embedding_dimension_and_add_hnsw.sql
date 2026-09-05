ALTER TABLE document_chunk
    ALTER COLUMN embedding TYPE vector(1024) USING embedding::vector(1024);
ALTER TABLE document_chunk ADD COLUMN embedding_model TEXT;
CREATE INDEX idx_document_chunk_embedding_hnsw
    ON document_chunk USING hnsw (embedding vector_cosine_ops);
