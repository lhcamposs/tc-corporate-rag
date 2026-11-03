package com.lhcamposs.tc_corporate_rag.services;

import com.lhcamposs.tc_corporate_rag.exceptions.DocumentProcessingException;
import com.lhcamposs.tc_corporate_rag.exceptions.LexicalSearchException;
import com.lhcamposs.tc_corporate_rag.exceptions.LlmIntegrationException;
import com.lhcamposs.tc_corporate_rag.vectorstore.VectorStoreRegistry;
import com.lhcamposs.tc_corporate_rag.vectorstore.VectorStoreType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    private static final String NOME_ARQUIVO = "politica-ferias.pdf";

    private static final String[] LINHAS_PDF = {
            "Politica de Ferias da Empresa",
            "Colaboradores tem direito a 30 dias corridos de ferias por ano trabalhado.",
            "As ferias podem ser fracionadas em ate tres periodos."
    };

    @Mock
    private VectorStore pgVectorStore;

    @Mock
    private VectorStore chromaVectorStore;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Captor
    private ArgumentCaptor<List<Document>> chunksCaptor;

    private IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        VectorStoreRegistry registry = new VectorStoreRegistry(Map.of(
                VectorStoreType.PGVECTOR, pgVectorStore,
                VectorStoreType.CHROMA, chromaVectorStore));
        ingestionService = new IngestionService(registry, jdbcTemplate);
    }

    @Test
    void ingestPdf_DevePersistirChunksNoVectorStoreENoBancoRelacional_QuandoPdfValido() throws IOException {
        // Arrange
        Resource pdf = criarPdf(LINHAS_PDF);

        // Act
        int total = ingestionService.ingestPdf(pdf, NOME_ARQUIVO);

        // Assert
        assertTrue(total >= 1, "Deve gerar ao menos um chunk");

        verify(pgVectorStore).add(chunksCaptor.capture());
        List<Document> chunks = chunksCaptor.getValue();
        assertEquals(total, chunks.size());
        assertTrue(chunks.get(0).getText().replace(" ", "").contains("30diascorridos"));

        // Um INSERT por chunk na tabela lexical, com o nome do arquivo de origem
        verify(jdbcTemplate, times(total)).update(anyString(), eq(NOME_ARQUIVO), anyInt(), anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestPdf_DeveGravarOMesmoCorpusComOsMesmosIdsNosDoisBancosENaTabelaLexical() throws IOException {
        // Act
        ingestionService.ingestPdf(criarPdf(LINHAS_PDF), NOME_ARQUIVO);

        // Assert: pgvector e ChromaDB recebem exatamente os mesmos chunks e IDs
        ArgumentCaptor<List<Document>> chromaCaptor = ArgumentCaptor.forClass(List.class);
        verify(pgVectorStore).add(chunksCaptor.capture());
        verify(chromaVectorStore).add(chromaCaptor.capture());
        List<Document> noPg = chunksCaptor.getValue();
        List<Document> noChroma = chromaCaptor.getValue();
        assertEquals(noPg.stream().map(Document::getId).toList(), noChroma.stream().map(Document::getId).toList());
        assertEquals(noPg.stream().map(Document::getText).toList(), noChroma.stream().map(Document::getText).toList());

        // E a tabela lexical guarda o mesmo ID (rastreio por chunk)
        verify(jdbcTemplate).update(IngestionSupport.LEXICAL_UPSERT_SQL,
                NOME_ARQUIVO, 0, noPg.get(0).getId(), noPg.get(0).getText());
    }

    @Test
    void ingestPdf_DeveLancarLlmIntegrationException_IndicandoOBanco_QuandoChromaFalha() throws IOException {
        // Arrange
        doThrow(new RuntimeException("Chroma offline")).when(chromaVectorStore).add(anyList());

        // Act & Assert
        LlmIntegrationException ex = assertThrows(LlmIntegrationException.class,
                () -> ingestionService.ingestPdf(criarPdf(LINHAS_PDF), NOME_ARQUIVO));
        assertTrue(ex.getMessage().contains("chroma"));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void ingestPdf_DeveGerarOsMesmosIdsEMetadados_AoReingerirOMesmoArquivo() throws IOException {
        // Act: o mesmo PDF é enviado duas vezes
        int primeira = ingestionService.ingestPdf(criarPdf(LINHAS_PDF), NOME_ARQUIVO);
        int segunda = ingestionService.ingestPdf(criarPdf(LINHAS_PDF), NOME_ARQUIVO);

        // Assert
        assertEquals(primeira, segunda);
        verify(pgVectorStore, times(2)).add(chunksCaptor.capture());
        List<Document> chunks1 = chunksCaptor.getAllValues().get(0);
        List<Document> chunks2 = chunksCaptor.getAllValues().get(1);

        // IDs idênticos => o upsert do pgvector atualiza em vez de duplicar
        assertEquals(chunks1.stream().map(Document::getId).toList(),
                chunks2.stream().map(Document::getId).toList());
        assertEquals(IngestionSupport.idDoChunk(NOME_ARQUIVO, 0), chunks1.get(0).getId());

        // Metadados de rastreio presentes
        assertEquals(NOME_ARQUIVO, chunks1.get(0).getMetadata().get("source_file"));
        assertEquals(0, chunks1.get(0).getMetadata().get("chunk_index"));
        assertEquals("pdf", chunks1.get(0).getMetadata().get("source"));
    }

    @Test
    void ingestPdf_DeveRemoverChunksObsoletosDaMesmaFonte_NosDoisArmazenamentos() throws IOException {
        // Act
        int total = ingestionService.ingestPdf(criarPdf(LINHAS_PDF), NOME_ARQUIVO);

        // Assert: tudo que está em posição >= total (versão anterior maior) é apagado
        verify(pgVectorStore).delete(any(Filter.Expression.class));
        verify(chromaVectorStore).delete(any(Filter.Expression.class));
        verify(jdbcTemplate).update(IngestionSupport.LEXICAL_DELETE_STALE_SQL, NOME_ARQUIVO, total);
    }

    @Test
    void ingestPdf_DeveLancarDocumentProcessingException_QuandoArquivoNaoEhPdfValido() {
        // Arrange
        Resource naoEhPdf = new ByteArrayResource("isto nao e um pdf".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return NOME_ARQUIVO;
            }
        };

        // Act & Assert
        assertThrows(DocumentProcessingException.class,
                () -> ingestionService.ingestPdf(naoEhPdf, NOME_ARQUIVO));

        verifyNoInteractions(pgVectorStore, chromaVectorStore, jdbcTemplate);
    }

    @Test
    void ingestPdf_DeveLancarLlmIntegrationException_QuandoFalhaNoVectorStore() throws IOException {
        // Arrange
        Resource pdf = criarPdf(LINHAS_PDF);
        doThrow(new RuntimeException("Ollama offline")).when(pgVectorStore).add(anyList());

        // Act & Assert
        assertThrows(LlmIntegrationException.class,
                () -> ingestionService.ingestPdf(pdf, NOME_ARQUIVO));

        // Se o embedding falhou, nada deve ter sido gravado na tabela lexical
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void ingestPdf_DeveLancarLexicalSearchException_QuandoFalhaNoInsertRelacional() throws IOException {
        // Arrange
        Resource pdf = criarPdf(LINHAS_PDF);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Insert error"));

        // Act & Assert
        LexicalSearchException ex = assertThrows(LexicalSearchException.class,
                () -> ingestionService.ingestPdf(pdf, NOME_ARQUIVO));

        assertFalse(ex.getMessage().isBlank());
    }

    /**
     * Gera um PDF real e mínimo em memória (PDFBox), para exercitar o
     * PagePdfDocumentReader e o TokenTextSplitter de verdade, sem mocks
     * e sem arquivos externos.
     */
    @Test
    void ingestPdf_DeveNormalizarEspacosRepetidosNosChunks() throws IOException {
        // Arrange: texto com espaços de preenchimento, como o extraído de PDFs com colunas
        Resource pdf = criarPdf("Politica      de     Ferias", "Colaboradores       tem    direito    a    30    dias.");

        // Act
        ingestionService.ingestPdf(pdf, NOME_ARQUIVO);

        // Assert: nenhum chunk gravado contém dois espaços seguidos
        verify(pgVectorStore).add(chunksCaptor.capture());
        for (Document chunk : chunksCaptor.getValue()) {
            assertFalse(chunk.getText().contains("  "), "Chunk com espaços repetidos: " + chunk.getText());
        }
    }

    private static Resource criarPdf(String... linhas) throws IOException {
        try (PDDocument documento = new PDDocument();
             ByteArrayOutputStream saida = new ByteArrayOutputStream()) {

            PDPage pagina = new PDPage();
            documento.addPage(pagina);

            try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
                conteudo.beginText();
                conteudo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                conteudo.newLineAtOffset(50, 700);
                for (String linha : linhas) {
                    conteudo.showText(linha);
                    conteudo.newLineAtOffset(0, -16);
                }
                conteudo.endText();
            }

            documento.save(saida);

            return new ByteArrayResource(saida.toByteArray()) {
                @Override
                public String getFilename() {
                    return NOME_ARQUIVO;
                }
            };
        }
    }
}
