CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_key TEXT NOT NULL,
    title TEXT NOT NULL,
    document_type TEXT NOT NULL,
    service_name TEXT,
    source_uri TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_source_key UNIQUE (source_key),
    CONSTRAINT ck_document_source_key_not_blank CHECK (btrim(source_key) <> ''),
    CONSTRAINT ck_document_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_document_type_not_blank CHECK (btrim(document_type) <> ''),
    CONSTRAINT ck_document_source_uri_not_blank CHECK (btrim(source_uri) <> ''),
    CONSTRAINT ck_document_content_hash_sha256 CHECK (content_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_document_type_service
    ON document (document_type, service_name);

CREATE TABLE document_chunk (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    parent_chunk_id UUID REFERENCES document_chunk (id) ON DELETE SET NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding VECTOR,
    search_vector TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('simple'::regconfig, coalesce(content, ''))
    ) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_chunk_position UNIQUE (document_id, chunk_index),
    CONSTRAINT ck_document_chunk_index_non_negative CHECK (chunk_index >= 0),
    CONSTRAINT ck_document_chunk_content_not_blank CHECK (btrim(content) <> ''),
    CONSTRAINT ck_document_chunk_metadata_object CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX idx_document_chunk_parent
    ON document_chunk (parent_chunk_id);

CREATE INDEX idx_document_chunk_metadata_gin
    ON document_chunk USING GIN (metadata jsonb_path_ops);

CREATE INDEX idx_document_chunk_search_vector_gin
    ON document_chunk USING GIN (search_vector);
