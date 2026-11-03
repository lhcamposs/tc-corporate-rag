package com.lhcamposs.tc_corporate_rag.vectorstore;

import com.lhcamposs.tc_corporate_rag.exceptions.VectorStoreNotAvailableException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class VectorStoreRegistryTest {

    @Test
    void construtorPorLista_DeveClassificarOsBancosPelaClasseConcreta() {
        PgVectorStore pg = mock(PgVectorStore.class);
        ChromaVectorStore chroma = mock(ChromaVectorStore.class);

        // a ordem da lista não pode importar
        VectorStoreRegistry registry = new VectorStoreRegistry(List.<VectorStore>of(chroma, pg));

        assertSame(pg, registry.get(VectorStoreType.PGVECTOR));
        assertSame(chroma, registry.get(VectorStoreType.CHROMA));
    }

    @Test
    void todos_DeveListarOsBancosEmOrdemEstavel() {
        VectorStore pg = mock(VectorStore.class);
        VectorStore chroma = mock(VectorStore.class);
        VectorStoreRegistry registry = new VectorStoreRegistry(Map.of(
                VectorStoreType.CHROMA, chroma,
                VectorStoreType.PGVECTOR, pg));

        assertEquals(List.of(VectorStoreType.PGVECTOR, VectorStoreType.CHROMA),
                List.copyOf(registry.all().keySet()));
    }

    @Test
    void get_DeveLancarExcecaoClara_QuandoBancoNaoEstaHabilitado() {
        VectorStoreRegistry registry = new VectorStoreRegistry(
                Map.of(VectorStoreType.PGVECTOR, mock(VectorStore.class)));

        VectorStoreNotAvailableException ex = assertThrows(VectorStoreNotAvailableException.class,
                () -> registry.get(VectorStoreType.CHROMA));

        assertTrue(ex.getMessage().contains("chroma"));
        assertTrue(ex.getMessage().contains("pgvector"), "A mensagem deve listar o que está disponível");
    }

    @Test
    void todos_NaoDevePermitirAlterarOMapaInterno() {
        VectorStoreRegistry registry = new VectorStoreRegistry(
                Map.of(VectorStoreType.PGVECTOR, mock(VectorStore.class)));

        assertThrows(UnsupportedOperationException.class,
                () -> registry.all().put(VectorStoreType.CHROMA, mock(VectorStore.class)));
    }
}
