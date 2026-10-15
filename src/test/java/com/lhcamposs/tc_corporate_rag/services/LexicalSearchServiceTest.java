package com.lhcamposs.tc_corporate_rag.services;

import com.lhcamposs.tc_corporate_rag.dto.RecoveredSource;
import com.lhcamposs.tc_corporate_rag.exceptions.LexicalSearchException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LexicalSearchServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private LexicalSearchService lexicalSearchService;

    @Test
    void buscarPorTermo_DeveRetornarListaDeFontes_QuandoSucesso() {
        String termo = "teste";
        List<RecoveredSource> expectedResults = List.of(
                new RecoveredSource("id1", "file1.pdf", 0, "conteudo 1", 0.9),
                new RecoveredSource("id2", "file1.pdf", 1, "conteudo 2", 0.8)
        );

        when(jdbcTemplate.query(eq(LexicalSearchService.BUSCA_SQL), any(org.springframework.jdbc.core.RowMapper.class), eq(termo), eq(LexicalSearchService.LIMITE_RESULTADOS)))
                .thenReturn(expectedResults);

        List<RecoveredSource> actualResults = lexicalSearchService.buscarPorTermo(termo);

        assertEquals(expectedResults, actualResults);
        assertEquals(2, actualResults.size());
    }

    @Test
    void buscarPorTermo_DeveLancarLexicalSearchException_QuandoOcorreErroNoBanco() {
        String termo = "teste";
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), anyString(), anyInt()))
                .thenThrow(new QueryTimeoutException("Timeout"));

        LexicalSearchException exception = assertThrows(LexicalSearchException.class, () -> {
            lexicalSearchService.buscarPorTermo(termo);
        });

        assertTrue(exception.getMessage().contains("Failed to search for term"));
    }
}
