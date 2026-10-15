-- Migração 002 — baseline lexical com busca textual do PostgreSQL
--
-- Substitui o "ILIKE '%termo%'" por full-text search: sem diferenciar acentos
-- ("ferias" encontra "férias"), com stemming em português ("férias"/"feriado"
-- compartilham radical), ignorando stopwords e ordenando por relevância.
--
-- Pré-requisito: migração 001 aplicada.
-- Esta migração é REEXECUTÁVEL (tudo é IF NOT EXISTS) e não perde dados.
--
--   docker exec -i <container_postgres> psql -U <usuario> -d tc_rag_db < db/migrations/002_baseline_lexical_fts.sql

BEGIN;

CREATE EXTENSION IF NOT EXISTS unaccent;

-- Configuração de busca "pt_unaccent": igual à 'portuguese', mas remove acentos
-- ANTES do stemming. É necessária porque unaccent() sozinha não é IMMUTABLE e,
-- por isso, não pode ser usada em coluna gerada nem em índice.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'pt_unaccent') THEN
        CREATE TEXT SEARCH CONFIGURATION pt_unaccent (COPY = portuguese);
        ALTER TEXT SEARCH CONFIGURATION pt_unaccent
            ALTER MAPPING FOR hword, hword_part, word
            WITH unaccent, portuguese_stem;
    END IF;
END
$$;

-- Vetor de busca mantido pelo próprio banco (recalculado a cada INSERT/UPDATE).
ALTER TABLE document_chunk
    ADD COLUMN IF NOT EXISTS content_tsv tsvector
        GENERATED ALWAYS AS (to_tsvector('pt_unaccent', content)) STORED;

CREATE INDEX IF NOT EXISTS idx_document_chunk_content_tsv
    ON document_chunk USING GIN (content_tsv);

COMMIT;
