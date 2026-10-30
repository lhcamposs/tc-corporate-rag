package com.lhcamposs.tc_corporate_rag.services;

import com.lhcamposs.tc_corporate_rag.dto.RecoveredSource;
import com.lhcamposs.tc_corporate_rag.dto.RetrievalResponse;
import com.lhcamposs.tc_corporate_rag.exceptions.VectorStoreNotAvailableException;
import com.lhcamposs.tc_corporate_rag.vectorstore.VectorStoreRegistry;
import com.lhcamposs.tc_corporate_rag.vectorstore.VectorStoreType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @Mock
    private RagQueryService ragQueryService;

    @Mock
    private LexicalSearchService lexicalSearchService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private VectorStoreRegistry vectorStoreRegistry;

    private EvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        evaluationService = new EvaluationService(ragQueryService, lexicalSearchService, jdbcTemplate, vectorStoreRegistry);
    }

    @Test
    void recuperar_DeveUsarBuscaLexical_QuandoMetodoLexical() {
        RecoveredSource trecho = new RecoveredSource("id-1", "Ferias.pdf", 0, "texto", 0.5);
        when(lexicalSearchService.buscarPorTermo("ferias", 10)).thenReturn(List.of(trecho));

        RetrievalResponse resposta = evaluationService.recuperar("ferias", " Lexical ", 10);

        assertEquals("lexical", resposta.metodo());
        assertEquals(List.of(trecho), resposta.fontes());
        verifyNoInteractions(ragQueryService);
    }

    @Test
    void recuperar_DeveUsarBancoVetorial_QuandoMetodoChroma() {
        Document documento = Document.builder()
                .id("id-2")
                .text("texto")
                .metadata(Map.of("source_file", "PSI.pdf", "chunk_index", 3))
                .build();
        when(ragQueryService.recuperar("senha", VectorStoreType.CHROMA, 10)).thenReturn(List.of(documento));

        RetrievalResponse resposta = evaluationService.recuperar("senha", "chroma", 10);

        assertEquals("chroma", resposta.metodo());
        assertEquals("id-2", resposta.fontes().getFirst().vectorId());
        assertEquals(3, resposta.fontes().getFirst().chunkIndex());
        verifyNoInteractions(lexicalSearchService);
    }

    @Test
    void recuperar_DeveLancarExcecao_QuandoMetodoDesconhecido() {
        assertThrows(VectorStoreNotAvailableException.class,
                () -> evaluationService.recuperar("pergunta", "elastic", 10));
    }
}
