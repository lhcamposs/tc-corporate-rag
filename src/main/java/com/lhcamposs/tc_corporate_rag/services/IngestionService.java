package com.lhcamposs.tc_corporate_rag.services;

import com.lhcamposs.tc_corporate_rag.exceptions.DocumentProcessingException;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import com.lhcamposs.tc_corporate_rag.vectorstore.VectorStoreRegistry;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Responsável pelo pipeline de ingestão:
 *   PDF -> extração de texto -> chunking -> embeddings -> bancos vetoriais (pgvector e ChromaDB)
 *
 * O texto extraído é normalizado antes do chunking e chunks muito curtos são
 * unidos ao vizinho (ver {@link PdfTextCleaner}).
 *
 * Além de salvar no VectorStore (para a busca semântica), também guarda os
 * chunks em texto puro numa tabela relacional (document_chunk) — é isso que
 * alimenta a busca lexical (baseline SQL LIKE) usada na Fase 4 de avaliação.
 *
 * A ingestão é idempotente: a identidade de um documento é o nome do arquivo.
 * Reenviar o mesmo PDF (ou uma versão atualizada dele) substitui os chunks
 * anteriores em vez de duplicá-los (ver {@link IngestionSupport}).
 */
@Service
public class IngestionService {

    private final VectorStoreRegistry vectorStoreRegistry;
    private final JdbcTemplate jdbcTemplate;

    public IngestionService(VectorStoreRegistry vectorStoreRegistry, JdbcTemplate jdbcTemplate) {
        this.vectorStoreRegistry = vectorStoreRegistry;
        this.jdbcTemplate = jdbcTemplate;
    }

    public int ingestPdf(Resource pdfResource, String nomeArquivo) {
        List<Document> chunks;

        try {
            // 1. Extrai o texto do PDF (cada página vira um Document)
            PagePdfDocumentReader reader = new PagePdfDocumentReader(pdfResource);
            // 1.1 Normaliza o texto: remove espaços de preenchimento e caracteres invisíveis
            List<Document> pages = PdfTextCleaner.normalizarDocumentos(reader.get());

            // 2. Divide em chunks menores, melhora a precisão da recuperação
            TokenTextSplitter splitter = TokenTextSplitter.builder()
                    .withChunkSize(800)              // Tamanho alvo de tokens por bloco
                    .withMinChunkSizeChars(350)      // Evita blocos minúsculos irrelevantes
                    .withMinChunkLengthToEmbed(5)    // Comprimento mínimo aceitável
                    .withKeepSeparator(true)         // Mantém quebras de linha/parágrafos estruturais
                    .build();

            // 2.1 Une ao vizinho os chunks muito curtos (sobras de quebra de página, rodapés)
            List<Document> divididos = PdfTextCleaner.unirChunksCurtos(
                    splitter.apply(pages), PdfTextCleaner.MIN_CHARS_CHUNK);

            // 3. Atribui ID determinístico e metadados de rastreio a cada chunk
            chunks = IngestionSupport.comIdentidadeEstavel(divididos, nomeArquivo, "pdf");
        } catch (Exception e) {
            throw new DocumentProcessingException("Failed to extract and fragment text from the PDF document: " + nomeArquivo, e);
        }

        // 4. Gera embeddings + upsert nos bancos vetoriais e na tabela lexical,
        //    removendo chunks obsoletos de versões anteriores do mesmo arquivo
        IngestionSupport.sync(vectorStoreRegistry.all(), jdbcTemplate, nomeArquivo, chunks);

        return chunks.size();
    }
}
