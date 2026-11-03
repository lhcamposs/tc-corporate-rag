package com.lhcamposs.tc_corporate_rag.controllers;

import com.lhcamposs.tc_corporate_rag.exceptions.DocumentProcessingException;
import com.lhcamposs.tc_corporate_rag.exceptions.LlmIntegrationException;
import com.lhcamposs.tc_corporate_rag.services.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestionController.class)
class IngestionControllerTest {

    private static final String URL = "/api/documents/upload";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionService ingestionService;

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("arquivo", "doc.pdf", "application/pdf", "conteudo".getBytes());
    }

    @Test
    void upload_DeveRetornar200ComTotalDeChunks_QuandoArquivoProcessado() throws Exception {
        when(ingestionService.ingestPdf(any(Resource.class), eq("doc.pdf"))).thenReturn(5);

        mockMvc.perform(multipart(URL).file(pdf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arquivo").value("doc.pdf"))
                .andExpect(jsonPath("$.totalChunks").value(5))
                .andExpect(jsonPath("$.status").value("PROCESSED"));
    }

    @Test
    void upload_DeveRetornar422_QuandoPdfNaoPodeSerProcessado() throws Exception {
        when(ingestionService.ingestPdf(any(Resource.class), eq("doc.pdf")))
                .thenThrow(new DocumentProcessingException("PDF invalido", new RuntimeException()));

        mockMvc.perform(multipart(URL).file(pdf()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error").value("Document Processing Error"));
    }

    @Test
    void upload_DeveRetornar502_QuandoLlmIndisponivel() throws Exception {
        when(ingestionService.ingestPdf(any(Resource.class), eq("doc.pdf")))
                .thenThrow(new LlmIntegrationException("Ollama offline", new RuntimeException()));

        mockMvc.perform(multipart(URL).file(pdf()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM Integration Error"));
    }
}
