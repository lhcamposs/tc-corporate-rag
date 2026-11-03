package com.lhcamposs.tc_corporate_rag.controllers;

import com.lhcamposs.tc_corporate_rag.dto.RagResponse;
import com.lhcamposs.tc_corporate_rag.dto.RecoveredSource;
import com.lhcamposs.tc_corporate_rag.vectorstore.VectorStoreType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.lhcamposs.tc_corporate_rag.exceptions.LexicalSearchException;
import com.lhcamposs.tc_corporate_rag.exceptions.LlmIntegrationException;
import com.lhcamposs.tc_corporate_rag.services.LexicalSearchService;
import com.lhcamposs.tc_corporate_rag.services.RagQueryService;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QueryController.class)
class QueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagQueryService ragQueryService;

    @MockitoBean
    private LexicalSearchService lexicalSearchService;


    @Test
    void consultarRag_DeveRetornar200ComRespostaETempo_QuandoPerguntaValida() throws Exception {
        RagResponse response = new RagResponse("30 dias corridos.", List.of(), 100, 500);
        when(ragQueryService.responder(eq("Quantos dias de ferias?"), any())).thenReturn(response);

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\":\"Quantos dias de ferias?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resposta").value("30 dias corridos."))
                .andExpect(jsonPath("$.tempoRecuperacaoMs").isNumber());
    }

    @Test
    void consultarRag_DeveRetornar400_QuandoPerguntaEstaEmBranco() throws Exception {
        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.message").value(containsString("pergunta")));

        verifyNoInteractions(ragQueryService);
    }

    @Test
    void consultarRag_DeveRetornar400_QuandoPerguntaEstaAusente() throws Exception {
        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));

        verifyNoInteractions(ragQueryService);
    }

    @Test
    void consultarRag_DeveRetornar400_QuandoPerguntaExcede2000Caracteres() throws Exception {
        String perguntaLonga = "a".repeat(2001);

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\":\"" + perguntaLonga + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));

        verifyNoInteractions(ragQueryService);
    }

    @Test
    void consultarRag_DeveRetornar502_QuandoLlmIndisponivel() throws Exception {
        when(ragQueryService.responder(anyString(), any()))
                .thenThrow(new LlmIntegrationException("LLM offline", new RuntimeException()));

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\":\"pergunta\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM Integration Error"));
    }

    @Test
    void consultarBaseline_DeveRetornar200ComResultados_QuandoTermoValido() throws Exception {
        RecoveredSource source = new RecoveredSource("id1", "file.pdf", 0, "resultado 1", 0.9);
        when(lexicalSearchService.buscarPorTermo("ferias")).thenReturn(List.of(source));

        mockMvc.perform(get("/api/query/baseline").param("termo", "ferias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trecho").value("resultado 1"));
    }

    @Test
    void consultarBaseline_DeveRetornar400_QuandoTermoEstaEmBranco() throws Exception {
        mockMvc.perform(get("/api/query/baseline").param("termo", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));

        verifyNoInteractions(lexicalSearchService);
    }

    @Test
    void consultarBaseline_DeveRetornar500_QuandoFalhaNaBuscaLexical() throws Exception {
        when(lexicalSearchService.buscarPorTermo("ferias"))
                .thenThrow(new LexicalSearchException("erro no banco", new RuntimeException()));

        mockMvc.perform(get("/api/query/baseline").param("termo", "ferias"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Lexical Search Error"));
    }
}
