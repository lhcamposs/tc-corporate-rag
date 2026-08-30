-- Executado automaticamente pelo Postgres na primeira subida do container
-- (docker-entrypoint-initdb.d)

CREATE EXTENSION IF NOT EXISTS vector;

-- O Spring AI, ao inicializar o VectorStore (com initialize-schema: true no
-- application.yml), cria a tabela de embeddings automaticamente. Este script
-- só garante que a extensão pgvector já está disponível antes disso.
