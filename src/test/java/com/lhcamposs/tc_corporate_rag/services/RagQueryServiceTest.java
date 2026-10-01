package com.lhcamposs.tc_corporate_rag.services;

import com.lhcamposs.tc_corporate_rag.exceptions.LlmIntegrationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagQueryServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec responseSpec;

    @InjectMocks
    private RagQueryService ragQueryService;

    @Test
    void responder_DeveRetornarConteudo_QuandoSucesso() {
        String pergunta = "Qual o segredo da vida?";
        String respostaEsperada = "42";

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(respostaEsperada);

        String respostaObtida = ragQueryService.responder(pergunta);

        assertEquals(respostaEsperada, respostaObtida);
        verify(chatClient).prompt();
        verify(requestSpec).user(pergunta);
    }

    @Test
    void responder_DeveLancarLlmIntegrationException_QuandoOcorreErroNoLlm() {
        String pergunta = "Qual o segredo da vida?";
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("LLM offline"));

        LlmIntegrationException exception = assertThrows(LlmIntegrationException.class, () -> {
            ragQueryService.responder(pergunta);
        });

        assertTrue(exception.getMessage().contains("Failed to generate a response using the language model"));
    }
}
