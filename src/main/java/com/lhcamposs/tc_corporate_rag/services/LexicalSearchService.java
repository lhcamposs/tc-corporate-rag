package com.lhcamposs.tc_corporate_rag.services;

import com.lhcamposs.tc_corporate_rag.exceptions.LexicalSearchException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Baseline de comparação — objetivo específico (e) da proposta:
 * busca lexical simples via SQL LIKE, sem embeddings nem semântica.
 * Usada na Fase 4 para contrastar com a busca semântica + RAG.
 */
@Service
public class LexicalSearchService {

    private final JdbcTemplate jdbcTemplate;

    public LexicalSearchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> buscarPorTermo(String termo) {
        try {
            String termoLike = "%" + termo + "%";
            return jdbcTemplate.queryForList(
                    "SELECT content FROM document_chunk WHERE content ILIKE ? LIMIT 10",
                    String.class, termoLike
            );
        } catch (DataAccessException e) {
            throw new LexicalSearchException("Failed to search for term in the relational database.", e);
        }
    }
}
