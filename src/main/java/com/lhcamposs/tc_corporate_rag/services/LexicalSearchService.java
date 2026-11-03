package com.lhcamposs.tc_corporate_rag.services;

import com.lhcamposs.tc_corporate_rag.dto.RecoveredSource;
import com.lhcamposs.tc_corporate_rag.exceptions.LexicalSearchException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Baseline de comparação — objetivo específico (e) da proposta:
 * busca lexical (por palavras-chave), sem embeddings nem semântica.
 * Usada na Fase 4 para contrastar com a busca semântica + RAG.
 *
 * Implementada com o full-text search nativo do PostgreSQL, que é o que um
 * sistema relacional tradicional ofereceria na prática:
 *  - configuração 'pt_unaccent': stemming em português, remoção de stopwords
 *    e acentos ignorados ("ferias" encontra "férias");
 *  - os termos da consulta são combinados com OR, então perguntas em linguagem
 *    natural ainda recuperam trechos (sem isso, AND exigiria todas as palavras);
 *  - ordenação por relevância com ts_rank (normalização por tamanho do texto).
 *
 * Observação para a monografia: ts_rank é uma função de ranqueamento por
 * frequência de termos, não é BM25. Descrever a baseline como "full-text
 * search do PostgreSQL (tsvector + ts_rank)".
 *
 * Cada resultado traz o vector_id do chunk, o MESMO ID usado no pgvector e no
 * ChromaDB, então os três métodos podem ser comparados contra um gabarito único.
 *
 * Pré-requisito no banco: extensão unaccent, configuração pt_unaccent, coluna
 * content_tsv e índice GIN (scripts/init-pgvector.sql ou
 * db/migrations/002_baseline_lexical_fts.sql).
 */
@Service
public class LexicalSearchService {

    static final int LIMITE_RESULTADOS = 10;

    static final String BUSCA_SQL = """
            SELECT dc.vector_id, dc.source_file, dc.chunk_index, dc.content,
                   ts_rank(dc.content_tsv, consulta.q, 1) AS score
            FROM document_chunk dc
            CROSS JOIN (
                SELECT replace(plainto_tsquery('pt_unaccent', ?)::text, '&', '|')::tsquery AS q
            ) consulta
            WHERE dc.content_tsv @@ consulta.q
            ORDER BY score DESC, dc.id
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public LexicalSearchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RecoveredSource> buscarPorTermo(String termo) {
        return buscarPorTermo(termo, LIMITE_RESULTADOS);
    }

    public List<RecoveredSource> buscarPorTermo(String termo, int limite) {
        try {
            return jdbcTemplate.query(BUSCA_SQL, LexicalSearchService::mapear, termo, limite);
        } catch (DataAccessException e) {
            throw new LexicalSearchException("Failed to search for term in the relational database.", e);
        }
    }

    static RecoveredSource mapear(ResultSet rs, int rowNum) throws SQLException {
        return new RecoveredSource(
                rs.getString("vector_id"),
                rs.getString("source_file"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getDouble("score"));
    }
}
