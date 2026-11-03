package com.lhcamposs.tc_corporate_rag.vectorstore;

import com.lhcamposs.tc_corporate_rag.exceptions.VectorStoreNotAvailableException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorStoreTypeTest {

    @Test
    void from_DeveConverterSemDiferenciarMaiusculasEEspacos() {
        assertEquals(VectorStoreType.PGVECTOR, VectorStoreType.from("pgvector"));
        assertEquals(VectorStoreType.PGVECTOR, VectorStoreType.from("PGVector"));
        assertEquals(VectorStoreType.CHROMA, VectorStoreType.from("chroma"));
        assertEquals(VectorStoreType.CHROMA, VectorStoreType.from("  CHROMA "));
    }

    @Test
    void from_DeveUsarPgvectorComoPadrao_QuandoNuloOuEmBranco() {
        assertEquals(VectorStoreType.PGVECTOR, VectorStoreType.from(null));
        assertEquals(VectorStoreType.PGVECTOR, VectorStoreType.from("   "));
    }

    @Test
    void from_DeveLancarExcecao_QuandoValorDesconhecido() {
        VectorStoreNotAvailableException ex = assertThrows(VectorStoreNotAvailableException.class,
                () -> VectorStoreType.from("pinecone"));
        assertTrue(ex.getMessage().contains("pinecone"));
    }

    @Test
    void getNome_DeveSerOValorAceitoNoParametroStore() {
        assertEquals("pgvector", VectorStoreType.PGVECTOR.getNome());
        assertEquals("chroma", VectorStoreType.CHROMA.getNome());
    }
}
