package com.lhcamposs.tc_corporate_rag.services;

import com.lhcamposs.tc_corporate_rag.exceptions.LlmIntegrationException;
import com.lhcamposs.tc_corporate_rag.dto.RecoveredSource;
import com.lhcamposs.tc_corporate_rag.vectorstore.VectorStoreRegistry;
import com.lhcamposs.tc_corporate_rag.vectorstore.VectorStoreType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagQueryServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private VectorStoreRegistry vectorStoreRegistry;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec responseSpec;

    private RagQueryService ragQueryService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // Inicializa manualmente devido aos parâmetros @Value
        ragQueryService = new RagQueryService(chatClient, vectorStoreRegistry, 4, 0.0);
    }

    @Test
    void responder_DeveRetornarConteudo_QuandoSucesso() {
        String pergunta = "Qual o segredo da vida?";
        String respostaEsperada = "42";

        when(vectorStoreRegistry.get(any())).thenReturn(vectorStore);
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenReturn(List.of());

        when(chatClient.prompt(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(respostaEsperada);

        com.lhcamposs.tc_corporate_rag.dto.RagResponse respostaObtida = ragQueryService.responder(pergunta, VectorStoreType.PGVECTOR);

        assertEquals(respostaEsperada, respostaObtida.resposta());
        verify(vectorStoreRegistry).get(VectorStoreType.PGVECTOR);
    }

    @Test
    void responder_DeveLancarLlmIntegrationException_QuandoOcorreErroNoLlm() {
        String pergunta = "Qual o segredo da vida?";
        when(vectorStoreRegistry.get(any())).thenReturn(vectorStore);
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenReturn(List.of());

        when(chatClient.prompt(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("LLM offline"));

        LlmIntegrationException exception = assertThrows(LlmIntegrationException.class, () -> {
            ragQueryService.responder(pergunta, VectorStoreType.PGVECTOR);
        });

        assertTrue(exception.getMessage().contains("Failed to generate a response using the language model"));
    }
}
