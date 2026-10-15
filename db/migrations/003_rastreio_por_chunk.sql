-- Migração 003 — rastreio por chunk (vector_id)
--
-- Adiciona document_chunk.vector_id: o MESMO ID usado no pgvector e no ChromaDB
-- para aquele chunk. Com ele, os resultados da baseline lexical, do pgvector e
-- do ChromaDB podem ser comparados contra um gabarito único.
--
-- Pré-requisito: migrações 001 e 002 aplicadas.
-- Esta migração é REEXECUTÁVEL e não apaga dados.
--
--   docker exec -i <container_postgres> psql -U <usuario> -d tc_rag_db < db/migrations/003_rastreio_por_chunk.sql
--
-- Linhas existentes são preenchidas com o ID determinístico que o código Java
-- calcula (UUID.nameUUIDFromBytes("<source_file>#<chunk_index>")), reproduzido aqui
-- em SQL (MD5 + bits de versão 3 e variante RFC 4122). Assim, os vetores já gravados
-- no pgvector (ingeridos depois da migração 001) continuam casando com as linhas.

BEGIN;

ALTER TABLE document_chunk
    ADD COLUMN IF NOT EXISTS vector_id UUID;

UPDATE document_chunk dc
SET vector_id = (
    substr(t.h, 1, 8) || '-' ||
    substr(t.h, 9, 4) || '-' ||
    '3' || substr(t.h, 14, 3) || '-' ||
    lpad(to_hex((('x' || substr(t.h, 17, 2))::bit(8)::int & 63) | 128), 2, '0') || substr(t.h, 19, 2) || '-' ||
    substr(t.h, 21, 12)
)::uuid
FROM (
    SELECT id, md5(source_file || '#' || chunk_index::text) AS h
    FROM document_chunk
) t
WHERE dc.id = t.id
  AND dc.vector_id IS NULL;

ALTER TABLE document_chunk
    ALTER COLUMN vector_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_document_chunk_vector_id') THEN
        ALTER TABLE document_chunk
            ADD CONSTRAINT uq_document_chunk_vector_id UNIQUE (vector_id);
    END IF;
END
$$;

COMMIT;
