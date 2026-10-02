-- Migração 001 — ingestão idempotente
--
-- Aplique UMA vez em bancos que já existiam antes desta mudança
-- (bancos novos já nascem corretos via scripts/init-pgvector.sql).
--
--   docker exec -i <container_postgres> psql -U <usuario> -d tc_rag_db < db/migrations/001_ingestao_idempotente.sql
--
-- Depois, reingira os dados (POST /api/documents/upload e POST /api/relational/ingest).

BEGIN;

-- 1. document_chunk: remove duplicatas geradas por ingestões repetidas,
--    mantendo a linha mais recente de cada (source_file, chunk_index).
DELETE FROM document_chunk a
    USING document_chunk b
WHERE a.source_file = b.source_file
  AND a.chunk_index = b.chunk_index
  AND a.id < b.id;

-- 2. Garante a unicidade que o ON CONFLICT do código exige.
ALTER TABLE document_chunk
    ADD CONSTRAINT uq_document_chunk_source_index UNIQUE (source_file, chunk_index);

-- 3. vector_store: os vetores antigos têm IDs aleatórios (e duplicados), então
--    não dá para reaproveitá-los. Esvazie e reingira — os novos usam IDs
--    determinísticos e passam a ser atualizados por UPSERT.
TRUNCATE TABLE vector_store;

COMMIT;
