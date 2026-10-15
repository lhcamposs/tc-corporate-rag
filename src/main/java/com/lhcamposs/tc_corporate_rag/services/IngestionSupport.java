package com.lhcamposs.tc_corporate_rag.services;

import com.lhcamposs.tc_corporate_rag.exceptions.LexicalSearchException;
import com.lhcamposs.tc_corporate_rag.exceptions.LlmIntegrationException;
import com.lhcamposs.tc_corporate_rag.vectorstore.VectorStoreType;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lógica compartilhada pelas ingestões (PDF e base relacional) para que
 * reingerir a mesma fonte seja IDEMPOTENTE: rodar N vezes deixa o índice
 * exatamente no mesmo estado que rodar 1 vez.
 *
 * Como funciona:
 *  1. Cada chunk recebe um ID determinístico (fonte + posição). O PgVectorStore
 *     faz UPSERT por ID, então reingerir atualiza o vetor em vez de duplicar.
 *  2. A tabela lexical tem UNIQUE (source_file, chunk_index) e usa
 *     INSERT ... ON CONFLICT DO UPDATE. Ela guarda também o vector_id: o MESMO
 *     ID usado no pgvector e no ChromaDB, o que dá rastreio por chunk entre
 *     os três métodos de busca (gabarito único para a avaliação).
 *  3. Chunks que sobraram de uma versão anterior maior da fonte (ex.: o PDF
 *     encolheu) são removidos nos dois armazenamentos.
 *
 * A gravação é feita em TODOS os bancos vetoriais disponíveis (pgvector e, se
 * habilitado, ChromaDB), para que os dois tenham exatamente o mesmo corpus.
 *
 * A ordem é "gravar o novo e só depois apagar o antigo": se algo falhar no
 * meio, o índice nunca fica vazio e uma nova execução converge para o estado
 * correto.
 */
final class IngestionSupport {

    static final String LEXICAL_UPSERT_SQL =
            "INSERT INTO document_chunk (source_file, chunk_index, vector_id, content) VALUES (?, ?, ?::uuid, ?) "
                    + "ON CONFLICT (source_file, chunk_index) DO UPDATE "
                    + "SET vector_id = EXCLUDED.vector_id, content = EXCLUDED.content";

    static final String LEXICAL_DELETE_STALE_SQL =
            "DELETE FROM document_chunk WHERE source_file = ? AND chunk_index >= ?";

    private IngestionSupport() {
    }

    /** ID estável (UUID v3) derivado da fonte e da posição do chunk. */
    static String idDoChunk(String fonte, int indice) {
        return UUID.nameUUIDFromBytes((fonte + "#" + indice).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * Devolve cópias dos documentos com ID determinístico e metadados de
     * rastreio (source_file, chunk_index). Metadados já existentes são
     * preservados; "source" só é definido se ainda não existir.
     */
    static List<Document> comIdentidadeEstavel(List<Document> documentos, String fonte, String tipoFonte) {
        List<Document> resultado = new ArrayList<>(documentos.size());
        for (int i = 0; i < documentos.size(); i++) {
            Document original = documentos.get(i);

            Map<String, Object> metadata = new HashMap<>(original.getMetadata());
            metadata.putIfAbsent("source", tipoFonte);
            metadata.put("source_file", fonte);
            metadata.put("chunk_index", i);

            resultado.add(Document.builder()
                    .id(idDoChunk(fonte, i))
                    .text(original.getText())
                    .metadata(metadata)
                    .build());
        }
        return resultado;
    }

    /** Filtro que seleciona os chunks da fonte cuja posição passou do total atual. */
    static Filter.Expression filtroChunksObsoletos(String fonte, int totalAtual) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return b.and(b.eq("source_file", fonte), b.gte("chunk_index", totalAtual)).build();
    }

    /**
     * Grava os documentos (já com ID estável) em todos os bancos vetoriais e na
     * tabela lexical e remove o que ficou obsoleto em todos eles.
     * Se um dos bancos falhar, os demais podem já ter sido atualizados; rodar a
     * ingestão de novo converge todos para o mesmo estado (idempotência).
     */
    static void sync(Map<VectorStoreType, VectorStore> vectorStores, JdbcTemplate jdbcTemplate,
                     String fonte, List<Document> documentos) {
        for (Map.Entry<VectorStoreType, VectorStore> entrada : vectorStores.entrySet()) {
            try {
                if (!documentos.isEmpty()) {
                    entrada.getValue().add(documentos); // UPSERT por ID
                }
                entrada.getValue().delete(filtroChunksObsoletos(fonte, documentos.size()));
            } catch (Exception e) {
                throw new LlmIntegrationException(
                        "Failed to generate embeddings or update the vector store '" + entrada.getKey().getNome()
                                + "' for source '" + fonte + "'. Check if the store and the LLM model are running.", e);
            }
        }

        try {
            for (int i = 0; i < documentos.size(); i++) {
                Document documento = documentos.get(i);
                jdbcTemplate.update(LEXICAL_UPSERT_SQL, fonte, i, documento.getId(), documento.getText());
            }
            jdbcTemplate.update(LEXICAL_DELETE_STALE_SQL, fonte, documentos.size());
        } catch (Exception e) {
            throw new LexicalSearchException(
                    "Failed to save text chunks to the relational database for lexical search (source '"
                            + fonte + "').", e);
        }
    }
}