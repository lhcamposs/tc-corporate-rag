-- Script para criar tabelas customizadas que não são geradas automaticamente pelo Spring AI
-- O Spring Boot executa este arquivo automaticamente se estiver no classpath (src/main/resources)

CREATE TABLE IF NOT EXISTS document_chunk (
    id SERIAL PRIMARY KEY,
    source_file TEXT NOT NULL,
    chunk_index INT NOT NULL,
    vector_id UUID NOT NULL,
    content TEXT NOT NULL,
    content_tsv tsvector
        GENERATED ALWAYS AS (to_tsvector('portuguese', content)) STORED,
    CONSTRAINT uq_document_chunk_source_index UNIQUE (source_file, chunk_index),
    CONSTRAINT uq_document_chunk_vector_id UNIQUE (vector_id)
);

CREATE INDEX IF NOT EXISTS idx_document_chunk_content_tsv
    ON document_chunk USING GIN (content_tsv);
