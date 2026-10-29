package com.lhcamposs.tc_corporate_rag.services;

import com.lhcamposs.tc_corporate_rag.dto.QueryResponse;
import com.lhcamposs.tc_corporate_rag.dto.RecoveredSource;
import com.lhcamposs.tc_corporate_rag.dto.RetrievalResponse;
import com.lhcamposs.tc_corporate_rag.exceptions.EvaluationInputException;
import com.lhcamposs.tc_corporate_rag.vectorstore.VectorStoreType;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Operações usadas apenas pelo protocolo de avaliação (Seção 3.9 da monografia):
 *  - recuperação isolada nos três métodos (pgvector, chroma e lexical), com k configurável;
 *  - geração do "teto" (pontuação máxima relativa): o modelo responde recebendo
 *    os trechos do gabarito no lugar dos recuperados (Finardi et al., 2024);
 *  - exportação de todos os trechos, para a anotação do gabarito.
 */
@Service
public class EvaluationService {

    static final String METODO_LEXICAL = "lexical";

    static final String TRECHO_POR_ID_SQL = """
            SELECT vector_id::text AS vector_id, source_file, chunk_index, content
            FROM document_chunk
            WHERE vector_id = ?::uuid
            """;

    static final String TODOS_TRECHOS_SQL = """
            SELECT vector_id::text AS vector_id, source_file, chunk_index, content
            FROM document_chunk
            ORDER BY source_file, chunk_index
            """;

    private final RagQueryService ragQueryService;
    private final LexicalSearchService lexicalSearchService;
    private final JdbcTemplate jdbcTemplate;

    public EvaluationService(RagQueryService ragQueryService,
                             LexicalSearchService lexicalSearchService,
                             JdbcTemplate jdbcTemplate) {
        this.ragQueryService = ragQueryService;
        this.lexicalSearchService = lexicalSearchService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public RetrievalResponse recuperar(String pergunta, String metodo, int k) {
        String normalizado = metodo == null ? "" : metodo.trim().toLowerCase(Locale.ROOT);
        long inicio = System.nanoTime();
        List<RecoveredSource> fontes;
        if (METODO_LEXICAL.equals(normalizado)) {
            fontes = lexicalSearchService.buscarPorTermo(pergunta, k);
        } else {
            // Lança VectorStoreNotAvailableException (400) para valores desconhecidos
            VectorStoreType tipo = VectorStoreType.from(normalizado);
            normalizado = tipo.getNome();
            fontes = ragQueryService.recuperar(pergunta, tipo, k).stream().map(RecoveredSource::of).toList();
        }
        long fim = System.nanoTime();
        return new RetrievalResponse(normalizado, fontes, (fim - inicio) / 1_000_000);
    }

    public QueryResponse responderComGabarito(String pergunta, List<String> vectorIds) {
        List<Document> documentos = new ArrayList<>();
        for (String vectorId : vectorIds) {
            List<Document> encontrados = jdbcTemplate.query(TRECHO_POR_ID_SQL,
                    (rs, rowNum) -> Document.builder()
                            .id(rs.getString("vector_id"))
                            .text(rs.getString("content"))
                            .metadata(Map.of(
                                    "source_file", rs.getString("source_file"),
                                    "chunk_index", rs.getInt("chunk_index")))
                            .build(),
                    vectorId.trim());
            if (encontrados.isEmpty()) {
                throw new EvaluationInputException("vectorId not found in document_chunk: " + vectorId);
            }
            documentos.add(encontrados.getFirst());
        }

        long inicio = System.nanoTime();
        String resposta = ragQueryService.gerar(pergunta, documentos);
        long tempoGeracaoMs = (System.nanoTime() - inicio) / 1_000_000;

        List<RecoveredSource> fontes = documentos.stream().map(RecoveredSource::of).toList();
        return new QueryResponse(resposta, "teto", fontes, 0, tempoGeracaoMs, tempoGeracaoMs);
    }

    public List<RecoveredSource> listarTrechos() {
        return jdbcTemplate.query(TODOS_TRECHOS_SQL, (rs, rowNum) -> new RecoveredSource(
                rs.getString("vector_id"),
                rs.getString("source_file"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                null));
    }
}
