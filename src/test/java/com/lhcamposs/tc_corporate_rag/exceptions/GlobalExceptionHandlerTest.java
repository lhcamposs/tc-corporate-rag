package com.lhcamposs.tc_corporate_rag.exceptions;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final WebRequest request = mock(WebRequest.class);

    @Test
    void handleDocumentProcessingException_DeveRetornar422() {
        DocumentProcessingException ex = new DocumentProcessingException("Erro no PDF", new RuntimeException());
        when(request.getDescription(false)).thenReturn("uri=/test");

        ResponseEntity<ErrorResponse> response = handler.handleDocumentProcessingException(ex, request);

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode());
        assertEquals("Document Processing Error", response.getBody().error());
        assertEquals("Erro no PDF", response.getBody().message());
        assertEquals("/test", response.getBody().path());
    }

    @Test
    void handleLlmIntegrationException_DeveRetornar502() {
        LlmIntegrationException ex = new LlmIntegrationException("LLM erro", new RuntimeException());
        when(request.getDescription(false)).thenReturn("uri=/test");

        ResponseEntity<ErrorResponse> response = handler.handleLlmIntegrationException(ex, request);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("LLM Integration Error", response.getBody().error());
        assertEquals("LLM erro", response.getBody().message());
    }

    @Test
    void handleLexicalSearchException_DeveRetornar500() {
        LexicalSearchException ex = new LexicalSearchException("Search erro", new RuntimeException());
        when(request.getDescription(false)).thenReturn("uri=/test");

        ResponseEntity<ErrorResponse> response = handler.handleLexicalSearchException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Lexical Search Error", response.getBody().error());
        assertEquals("Search erro", response.getBody().message());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleConstraintViolationException_DeveRetornar400ComMensagensConcatenadas() {
        ConstraintViolation<Object> v1 = mock(ConstraintViolation.class);
        when(v1.getMessage()).thenReturn("O termo de busca não pode estar vazio.");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(v1));
        when(request.getDescription(false)).thenReturn("uri=/api/query/baseline");

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolationException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Validation Error", response.getBody().error());
        assertEquals("O termo de busca não pode estar vazio.", response.getBody().message());
        assertEquals("/api/query/baseline", response.getBody().path());
    }

    @Test
    void handleGenericException_DeveRetornar500SemVazarDetalhesInternos() {
        Exception ex = new IllegalStateException("senha do banco: 135791");
        when(request.getDescription(false)).thenReturn("uri=/test");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal Server Error", response.getBody().error());
        assertNotNull(response.getBody().timestamp());
        assertFalse(response.getBody().message().contains("senha"),
                "A mensagem interna da exceção não pode vazar para o cliente");
        assertTrue(response.getBody().message().contains("unexpected error"));
    }
}