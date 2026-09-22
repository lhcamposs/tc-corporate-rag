package com.lhcamposs.tc_corporate_rag.services;

import com.lhcamposs.tc_corporate_rag.exceptions.DocumentProcessingException;
import com.lhcamposs.tc_corporate_rag.exceptions.LexicalSearchException;
import com.lhcamposs.tc_corporate_rag.exceptions.LlmIntegrationException;
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

        if (documentos.isEmpty()) {
            return 0;
        }

        try {
            // 2. Gera os embeddings (via Ollama) e salva no pgvector.
            // Registros costumam ser curtos o suficiente para não precisar
            // de TokenTextSplitter aqui, diferente dos PDFs (IngestionService).
            vectorStore.add(documentos);
        } catch (Exception e) {
            throw new LlmIntegrationException(
                    "Failed to generate embeddings or connect to the vector store for the relational records.", e);
        }

        try {
            // 3. Também salva o texto puro em document_chunk, para manter a
            // busca lexical (baseline) enxergando a mesma base de conhecimento.
            for (int i = 0; i < documentos.size(); i++) {
                jdbcTemplate.update(
                        "INSERT INTO document_chunk (source_file, chunk_index, content) VALUES (?, ?, ?)",
                        "artigo_conhecimento", i, documentos.get(i).getText()
                );
            }
        } catch (Exception e) {
            throw new LexicalSearchException(
                    "Failed to save relational records to document_chunk for lexical search.", e);
        }

        return documentos.size();
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
