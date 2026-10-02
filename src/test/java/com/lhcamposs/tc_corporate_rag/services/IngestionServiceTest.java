package com.lhcamposs.tc_corporate_rag.services;

import com.lhcamposs.tc_corporate_rag.exceptions.DocumentProcessingException;
import com.lhcamposs.tc_corporate_rag.exceptions.LexicalSearchException;
import com.lhcamposs.tc_corporate_rag.exceptions.LlmIntegrationException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
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
    private VectorStore vectorStore;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Captor
    private ArgumentCaptor<List<Document>> chunksCaptor;

    @InjectMocks
    private IngestionService ingestionService;

    @Test
    void ingestPdf_DevePersistirChunksNoVectorStoreENoBancoRelacional_QuandoPdfValido() throws IOException {

        Resource pdf = criarPdf(LINHAS_PDF);

        int total = ingestionService.ingestPdf(pdf, NOME_ARQUIVO);

        assertTrue(total >= 1, "Deve gerar ao menos um chunk");

        verify(vectorStore).add(chunksCaptor.capture());
        List<Document> chunks = chunksCaptor.getValue();
        assertEquals(total, chunks.size());
        assertTrue(chunks.get(0).getText().replace(" ", "").contains("30diascorridos"));

        verify(jdbcTemplate, times(total)).update(anyString(), eq(NOME_ARQUIVO), anyInt(), anyString());
    }

    @Test
    void ingestPdf_DeveGerarOsMesmosIdsEMetadados_AoReingerirOMesmoArquivo() throws IOException {

        int primeira = ingestionService.ingestPdf(criarPdf(LINHAS_PDF), NOME_ARQUIVO);
        int segunda = ingestionService.ingestPdf(criarPdf(LINHAS_PDF), NOME_ARQUIVO);

        assertEquals(primeira, segunda);
        verify(vectorStore, times(2)).add(chunksCaptor.capture());
        List<Document> chunks1 = chunksCaptor.getAllValues().get(0);
        List<Document> chunks2 = chunksCaptor.getAllValues().get(1);

        assertEquals(chunks1.stream().map(Document::getId).toList(),
                chunks2.stream().map(Document::getId).toList());
        assertEquals(IngestionSupport.idDoChunk(NOME_ARQUIVO, 0), chunks1.get(0).getId());

        assertEquals(NOME_ARQUIVO, chunks1.get(0).getMetadata().get("source_file"));
        assertEquals(0, chunks1.get(0).getMetadata().get("chunk_index"));
        assertEquals("pdf", chunks1.get(0).getMetadata().get("source"));
    }

    @Test
    void ingestPdf_DeveRemoverChunksObsoletosDaMesmaFonte_NosDoisArmazenamentos() throws IOException {

        int total = ingestionService.ingestPdf(criarPdf(LINHAS_PDF), NOME_ARQUIVO);

        verify(vectorStore).delete(any(Filter.Expression.class));
        verify(jdbcTemplate).update(IngestionSupport.LEXICAL_DELETE_STALE_SQL, NOME_ARQUIVO, total);
    }

    @Test
    void ingestPdf_DeveLancarDocumentProcessingException_QuandoArquivoNaoEhPdfValido() {

        Resource naoEhPdf = new ByteArrayResource("isto nao e um pdf".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return NOME_ARQUIVO;
            }
        };

        assertThrows(DocumentProcessingException.class,
                () -> ingestionService.ingestPdf(naoEhPdf, NOME_ARQUIVO));

        verifyNoInteractions(vectorStore, jdbcTemplate);
    }

    @Test
    void ingestPdf_DeveLancarLlmIntegrationException_QuandoFalhaNoVectorStore() throws IOException {

        Resource pdf = criarPdf(LINHAS_PDF);
        doThrow(new RuntimeException("Ollama offline")).when(vectorStore).add(anyList());

        assertThrows(LlmIntegrationException.class,
                () -> ingestionService.ingestPdf(pdf, NOME_ARQUIVO));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void ingestPdf_DeveLancarLexicalSearchException_QuandoFalhaNoInsertRelacional() throws IOException {
        // Arrange
        Resource pdf = criarPdf(LINHAS_PDF);
        when(jdbcTemplate.update(anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("Insert error"));

        LexicalSearchException ex = assertThrows(LexicalSearchException.class,
                () -> ingestionService.ingestPdf(pdf, NOME_ARQUIVO));

        assertFalse(ex.getMessage().isBlank());
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
