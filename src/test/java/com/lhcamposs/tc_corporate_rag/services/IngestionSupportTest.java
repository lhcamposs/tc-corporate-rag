package com.lhcamposs.tc_corporate_rag.services;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IngestionSupportTest {

    @Test
    void idDoChunk_DeveSerDeterministico() {
        assertEquals(IngestionSupport.idDoChunk("a.pdf", 3), IngestionSupport.idDoChunk("a.pdf", 3));
    }

    @Test
    void idDoChunk_DeveDiferirPorArquivoEPorPosicao() {
        assertNotEquals(IngestionSupport.idDoChunk("a.pdf", 0), IngestionSupport.idDoChunk("a.pdf", 1));
        assertNotEquals(IngestionSupport.idDoChunk("a.pdf", 0), IngestionSupport.idDoChunk("b.pdf", 0));
    }

    @Test
    void idDoChunk_DeveSerUmUuidValido_ParaOPgVectorStore() {

        assertDoesNotThrow(() -> UUID.fromString(IngestionSupport.idDoChunk("a.pdf", 0)));
    }

    @Test
    void comIdentidadeEstavel_DeveAtribuirIdsEMetadadosPreservandoOsExistentes() {
        List<Document> originais = List.of(
                new Document("primeiro", Map.of("page_number", 1)),
                new Document("segundo", Map.of("source", "relational", "record_id", 9L)));

        List<Document> resultado = IngestionSupport.comIdentidadeEstavel(originais, "fonte.pdf", "pdf");

        assertEquals(2, resultado.size());
        assertEquals(IngestionSupport.idDoChunk("fonte.pdf", 0), resultado.get(0).getId());
        assertEquals(IngestionSupport.idDoChunk("fonte.pdf", 1), resultado.get(1).getId());
        assertEquals("primeiro", resultado.get(0).getText());

        assertEquals("fonte.pdf", resultado.get(0).getMetadata().get("source_file"));
        assertEquals(0, resultado.get(0).getMetadata().get("chunk_index"));
        assertEquals(1, resultado.get(1).getMetadata().get("chunk_index"));

        assertEquals(1, resultado.get(0).getMetadata().get("page_number"));
        assertEquals("pdf", resultado.get(0).getMetadata().get("source"));
        assertEquals("relational", resultado.get(1).getMetadata().get("source"));
        assertEquals(9L, resultado.get(1).getMetadata().get("record_id"));
    }

    @Test
    void comIdentidadeEstavel_NaoDeveAlterarOsDocumentosOriginais() {
        Document original = new Document("texto", Map.of("k", "v"));

        IngestionSupport.comIdentidadeEstavel(List.of(original), "f.pdf", "pdf");

        assertEquals(Map.of("k", "v"), original.getMetadata());
        assertNull(original.getMetadata().get("source_file"));
    }
}
