package com.lhcamposs.tc_corporate_rag.controllers;

import com.lhcamposs.tc_corporate_rag.exceptions.LlmIntegrationException;
import com.lhcamposs.tc_corporate_rag.services.RelationalIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RelationalIngestionController.class)
class RelationalIngestionControllerTest {

    private static final String URL = "/api/relational/ingest";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RelationalIngestionService relationalIngestionService;

    @Test
    void ingest_DeveRetornar200ComTotalDeRegistros_QuandoProcessado() throws Exception {
        when(relationalIngestionService.ingestarTabela()).thenReturn(10);

        mockMvc.perform(post(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tabela").value("artigo_conhecimento"))
                .andExpect(jsonPath("$.totalRegistros").value(10))
                .andExpect(jsonPath("$.status").value("PROCESSED"));
    }

    @Test
    void ingest_DeveRetornar502_QuandoLlmIndisponivel() throws Exception {
        when(relationalIngestionService.ingestarTabela())
                .thenThrow(new LlmIntegrationException("Ollama offline", new RuntimeException()));

        mockMvc.perform(post(URL))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM Integration Error"));
    }
}
