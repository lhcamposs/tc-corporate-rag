-- Script para criar tabelas customizadas que não são geradas automaticamente pelo Spring AI
-- O Spring Boot executa este arquivo automaticamente se estiver no classpath (src/main/resources)

CREATE TABLE IF NOT EXISTS document_chunk (
    id SERIAL PRIMARY KEY,
    source_file TEXT,
    chunk_index INT,
    content TEXT
);
