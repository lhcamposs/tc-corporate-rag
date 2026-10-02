package com.lhcamposs.tc_corporate_rag.services;

import com.lhcamposs.tc_corporate_rag.exceptions.DocumentProcessingException;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Service
public class RelationalIngestionService {

    private static final String FONTE = "artigo_conhecimento";

    private static final String SELECT_REGISTROS =
            "SELECT id, category, title, content FROM artigo_conhecimento ORDER BY id";

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public RelationalIngestionService(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    public int ingestarTabela() {
        List<Document> documentos;

        try {
            // 1. Consulta a base relacional e transforma cada linha em um Document
            documentos = jdbcTemplate.query(SELECT_REGISTROS, this::mapRowToDocument);
        } catch (Exception e) {
            throw new DocumentProcessingException(
                    "Failed to query and convert relational records from 'artigo_conhecimento' into documents.", e);
        }

        // 2. ID determinístico (fonte + posição) e metadados de rastreio.
        // Registros costumam ser curtos o suficiente para não precisar
        // de TokenTextSplitter aqui, diferente dos PDFs (IngestionService).
        List<Document> identificados = IngestionSupport.comIdentidadeEstavel(documentos, FONTE, "relational");

        // 3. Upsert no pgvector e em document_chunk (baseline lexical), removendo
        // o que não existe mais na tabela. Se a tabela estiver vazia, o índice
        // dessa fonte também é esvaziado, e rodar de novo não muda nada (idempotente).
        IngestionSupport.sincronizar(vectorStore, jdbcTemplate, FONTE, identificados);

        return identificados.size();
    }

    /**
     * Converte uma linha da tabela em um Document textual, com metadados
     * que identificam a origem — útil para depois rastrear, numa resposta
     * do RAG, se o trecho recuperado veio de um PDF ou de um registro
     * estruturado (campo "source" em Document.getMetadata()).
     */
    private Document mapRowToDocument(ResultSet rs, int rowNum) throws SQLException {
        long id = rs.getLong("id");
        String categoria = rs.getString("category");
        String titulo = rs.getString("title");
        String conteudo = rs.getString("content");

        String texto = "Categoria: %s\nTítulo: %s\n\n%s".formatted(categoria, titulo, conteudo);

        return new Document(texto, Map.of(
                "source", "relational",
                "table", "artigo_conhecimento",
                "record_id", id,
                "categoria", categoria == null ? "" : categoria
        ));
    }
}
