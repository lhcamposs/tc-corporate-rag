package com.lhcamposs.tc_corporate_rag.services;

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
    void buscarPorTermo_DeveRetornarListaDeStrings_QuandoSucesso() {
        String termo = "teste";
        String termoLike = "%teste%";
        List<String> expectedResults = List.of("resultado 1", "resultado 2");

        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(termoLike)))
                .thenReturn(expectedResults);

        List<String> actualResults = lexicalSearchService.buscarPorTermo(termo);

        assertEquals(expectedResults, actualResults);
        assertEquals(2, actualResults.size());
    }

    @Test
    void buscarPorTermo_DeveLancarLexicalSearchException_QuandoOcorreErroNoBanco() {
        String termo = "teste";
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyString()))
                .thenThrow(new QueryTimeoutException("Timeout"));

        LexicalSearchException exception = assertThrows(LexicalSearchException.class, () -> {
            lexicalSearchService.buscarPorTermo(termo);
        });

        assertTrue(exception.getMessage().contains("Failed to search for term"));
    }
}
