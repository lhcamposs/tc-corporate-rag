package com.lhcamposs.tc_corporate_rag.service;

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
        String termoLike = "%" + termo + "%";
        return jdbcTemplate.queryForList(
                "SELECT content FROM document_chunk WHERE content ILIKE ? LIMIT 10",
                String.class, termoLike
        );
    }
}
