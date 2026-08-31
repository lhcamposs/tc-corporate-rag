package com.lhcamposs.tc_corporate_rag.controllers;

import com.lhcamposs.tc_corporate_rag.dto.QueryRequest;
import com.lhcamposs.tc_corporate_rag.dto.QueryResponse;
import com.lhcamposs.tc_corporate_rag.services.LexicalSearchService;
import com.lhcamposs.tc_corporate_rag.services.RagQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints de consulta: RAG (busca semântica + geração) e busca lexical
 * (baseline), lado a lado, para facilitar a comparação da Fase 4.
 */
@RestController
@RequestMapping("/api/consultas")
public class QueryController {

    private final RagQueryService ragQueryService;
    private final LexicalSearchService lexicalSearchService;

    public QueryController(RagQueryService ragQueryService, LexicalSearchService lexicalSearchService) {
        this.ragQueryService = ragQueryService;
        this.lexicalSearchService = lexicalSearchService;
    }

    /**
     * POST /api/consultas
     * Corpo: { "pergunta": "..." }
     * Pergunta em linguagem natural -> resposta gerada via RAG
     * (embeddings + pgvector + LLM no Ollama).
     */
    @PostMapping
    public ResponseEntity<QueryResponse> consultarRag(@RequestBody QueryRequest request) {
        long inicio = System.currentTimeMillis();
        String resposta = ragQueryService.responder(request.pergunta());
        long duracao = System.currentTimeMillis() - inicio;

        return ResponseEntity.ok(new QueryResponse(resposta, duracao));
    }

    /**
     * GET /api/consultas/baseline?termo=...
     * Busca lexical simples (SQL LIKE) — baseline de comparação da Fase 4.
     */
    @GetMapping("/baseline")
    public ResponseEntity<List<String>> consultarBaseline(@RequestParam String termo) {
        return ResponseEntity.ok(lexicalSearchService.buscarPorTermo(termo));
    }
}
