package com.lhcamposs.tc_corporate_rag.services;

import com.lhcamposs.tc_corporate_rag.exceptions.DocumentProcessingException;
import com.lhcamposs.tc_corporate_rag.exceptions.LexicalSearchException;
import com.lhcamposs.tc_corporate_rag.exceptions.LlmIntegrationException;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonMetadataGenerator;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Responsável pelo pipeline de ingestão:
 *   PDF -> extração de texto -> chunking -> embeddings -> pgvector
 *
 * Além de salvar no VectorStore (para a busca semântica), também guarda os
 * chunks em texto puro numa tabela relacional (document_chunk) — é isso que
 * alimenta a busca lexical (baseline SQL LIKE) usada na Fase 4 de avaliação.
 */
@Service
public class IngestionService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public IngestionService(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    public int ingestPdf(Resource pdfResource, String nomeArquivo) {
        List<Document> chunks;

        try {
            // 1. Extrai o texto do PDF (cada página vira um Document)
            PagePdfDocumentReader reader = new PagePdfDocumentReader(pdfResource);
            List<Document> pages = reader.get();

            // 2. Divide em chunks menores, melhora a precisão da recuperação
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(800)              // Tamanho alvo de tokens por bloco
                    .withMinChunkSizeChars(350)      // Evita blocos minúsculos irrelevantes
                    .withMinChunkLengthToEmbed(5)    // Comprimento mínimo aceitável
                    .withKeepSeparator(true)         // Mantém quebras de linha/parágrafos estruturais
                    .build();

            chunks = splitter.apply(pages);
        } catch (Exception e) {
            throw new DocumentProcessingException("Failed to extract and fragment text from the PDF document: " + nomeArquivo, e);
        }

        try {
            // 3. Gera os embeddings (via Ollama) e salva no pgvector
            vectorStore.add(chunks);
        } catch (Exception e) {
            throw new LlmIntegrationException("Failed to generate embeddings or connect to the vector store. Check if the LLM model is running.", e);
        }

        try {
            // 4. Salva o texto puro na tabela relacional, para a busca lexical (baseline)
            for (int i = 0; i < chunks.size(); i++) {
                jdbcTemplate.update(
                        "INSERT INTO document_chunk (source_file, chunk_index, content) VALUES (?, ?, ?)",
                        nomeArquivo, i, chunks.get(i).getText()
                );
            }
        } catch (Exception e) {
            throw new LexicalSearchException("Failed to save text chunks to the relational database for lexical search.", e);
        }

        return chunks.size();

    }
}
