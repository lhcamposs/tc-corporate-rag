package com.lhcamposs.tc_corporate_rag.controllers;

import com.lhcamposs.tc_corporate_rag.exceptions.LexicalSearchException;
import com.lhcamposs.tc_corporate_rag.exceptions.LlmIntegrationException;
import com.lhcamposs.tc_corporate_rag.services.LexicalSearchService;
import com.lhcamposs.tc_corporate_rag.services.RagQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
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
        when(ragQueryService.responder("Quantos dias de ferias?")).thenReturn("30 dias corridos.");

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\":\"Quantos dias de ferias?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resposta").value("30 dias corridos."))
                .andExpect(jsonPath("$.tempoRespostaMs").isNumber());
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
        when(ragQueryService.responder("pergunta"))
                .thenThrow(new LlmIntegrationException("LLM offline", new RuntimeException()));

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\":\"pergunta\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM Integration Error"));
    }

    @Test
    void consultarBaseline_DeveRetornar200ComResultados_QuandoTermoValido() throws Exception {
        when(lexicalSearchService.buscarPorTermo("ferias")).thenReturn(List.of("resultado 1"));

        mockMvc.perform(get("/api/query/baseline").param("termo", "ferias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0]").value("resultado 1"));
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
