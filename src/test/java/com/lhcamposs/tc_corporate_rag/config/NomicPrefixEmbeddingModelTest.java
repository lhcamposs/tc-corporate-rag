package com.lhcamposs.tc_corporate_rag.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NomicPrefixEmbeddingModelTest {

    @Test
    void embedDeTexto_DeveUsarPrefixoDePergunta() {
        EmbeddingModel delegate = mock(EmbeddingModel.class);
        new NomicPrefixEmbeddingModel(delegate).embed("Quantos dias de férias?");
        verify(delegate).embed("search_query: Quantos dias de férias?");
    }

    @Test
    void call_DeveUsarPrefixoDeDocumento() {
        EmbeddingModel delegate = mock(EmbeddingModel.class);
        new NomicPrefixEmbeddingModel(delegate).call(new EmbeddingRequest(List.of("trecho A", "trecho B"), null));

        ArgumentCaptor<EmbeddingRequest> captor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(delegate).call(captor.capture());
        assertEquals(List.of("search_document: trecho A", "search_document: trecho B"), captor.getValue().getInstructions());
    }

    @Test
    void naoDeveDuplicarPrefixo() {
        assertEquals("search_query: x", NomicPrefixEmbeddingModel.comoPergunta("search_query: x"));
        assertEquals("search_document: x", NomicPrefixEmbeddingModel.comoDocumento("search_document: x"));
    }
}
