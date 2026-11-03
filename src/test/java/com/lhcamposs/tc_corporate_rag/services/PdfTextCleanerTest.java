package com.lhcamposs.tc_corporate_rag.services;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PdfTextCleanerTest {

    private static final int MIN = 300;

    // ------------------------------------------------------------ normalizar

    @Test
    void normalizar_DeveColapsarEspacosRepetidosEAparaAsLinhas() {
        String bruto = "EAZYBYTES      HR  POLICY    MANUAL      \n\n\n\n   Segunda   linha  ";

        assertEquals("EAZYBYTES HR POLICY MANUAL\n\nSegunda linha", PdfTextCleaner.normalizar(bruto));
    }

    @Test
    void normalizar_DeveRemoverCaracteresInvisiveisENaoQuebrarEspaco() {
        assertEquals("a b c", PdfTextCleaner.normalizar("a​ b  c"));
    }

    @Test
    void normalizar_DevePreservarAEstruturaDeListas() {
        assertEquals("● Item um\n● Item dois",
                PdfTextCleaner.normalizar("●​   Item um\r\n●​   Item dois"));
    }

    @Test
    void normalizar_DeveTratarNuloEVazio() {
        assertEquals("", PdfTextCleaner.normalizar(null));
        assertEquals("", PdfTextCleaner.normalizar("  \n \t "));
    }

    // ----------------------------------------------------------- unirCurtos

    @Test
    void unirCurtos_DeveUnirChunkCurtoAoAnterior() {
        List<String> textos = List.of("A".repeat(400), "curto", "B".repeat(350), "x");

        assertEquals(List.of("A".repeat(400) + "\ncurto", "B".repeat(350) + "\nx"),
                PdfTextCleaner.unirCurtos(textos, MIN));
    }

    @Test
    void unirCurtos_DeveUnirOPrimeiroCurtoAoSeguinte() {
        assertEquals(List.of("curto\n" + "B".repeat(350)),
                PdfTextCleaner.unirCurtos(List.of("curto", "B".repeat(350)), MIN));
    }

    @Test
    void unirCurtos_DeveAcumularVariosCurtosSeguidos() {
        assertEquals(List.of("a\nb\n" + "C".repeat(400)),
                PdfTextCleaner.unirCurtos(List.of("a", "b", "C".repeat(400)), MIN));
    }

    @Test
    void unirCurtos_DeveManterOUnicoChunkMesmoCurto() {
        assertEquals(List.of("so um"), PdfTextCleaner.unirCurtos(List.of("so um"), MIN));
    }

    @Test
    void unirCurtos_DeveDescartarVaziosETratarListaVazia() {
        assertEquals(List.of("D".repeat(400)), PdfTextCleaner.unirCurtos(List.of("", "  ", "D".repeat(400)), MIN));
        assertEquals(List.of(), PdfTextCleaner.unirCurtos(List.of(), MIN));
    }

    // ------------------------------------------------------------- Document

    @Test
    void unirChunksCurtos_DeveManterOsMetadadosDoPrimeiroTrechoDoGrupo() {
        List<Document> chunks = List.of(
                new Document("A".repeat(400), Map.of("page_number", 1)),
                new Document("curto", Map.of("page_number", 2)),
                new Document("B".repeat(400), Map.of("page_number", 3)));

        List<Document> resultado = PdfTextCleaner.unirChunksCurtos(chunks, MIN);

        assertEquals(2, resultado.size());
        assertEquals("A".repeat(400) + "\ncurto", resultado.get(0).getText());
        assertEquals(1, resultado.get(0).getMetadata().get("page_number"));
        assertEquals(3, resultado.get(1).getMetadata().get("page_number"));
    }

    @Test
    void normalizarDocumentos_DeveNormalizarEDescartarPaginasVazias() {
        List<Document> paginas = List.of(
                new Document("a    b", Map.of("page_number", 1)),
                new Document("\u200B \u200B", Map.of("page_number", 2)));

        List<Document> resultado = PdfTextCleaner.normalizarDocumentos(paginas);

        assertEquals(1, resultado.size());
        assertEquals("a b", resultado.get(0).getText());
        assertEquals(1, resultado.get(0).getMetadata().get("page_number"));
    }
}
