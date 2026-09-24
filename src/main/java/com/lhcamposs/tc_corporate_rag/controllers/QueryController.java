package com.lhcamposs.tc_corporate_rag.controllers;

import com.lhcamposs.tc_corporate_rag.dto.QueryRequest;
import com.lhcamposs.tc_corporate_rag.dto.QueryResponse;
import com.lhcamposs.tc_corporate_rag.services.LexicalSearchService;
import com.lhcamposs.tc_corporate_rag.services.RagQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/query")
@Validated
public class QueryController {

    private final RagQueryService ragQueryService;
    private final LexicalSearchService lexicalSearchService;

    public QueryController(RagQueryService ragQueryService, LexicalSearchService lexicalSearchService) {
        this.ragQueryService = ragQueryService;
        this.lexicalSearchService = lexicalSearchService;
    }

    @PostMapping
    public ResponseEntity<QueryResponse> consultarRag(@Valid @RequestBody QueryRequest request) {
        long inicio = System.currentTimeMillis();
        String resposta = ragQueryService.responder(request.pergunta());
        long duracao = System.currentTimeMillis() - inicio;

        return ResponseEntity.ok(new QueryResponse(resposta, duracao));
    }

    @GetMapping("/baseline")
    public ResponseEntity<List<String>> consultarBaseline(
            @RequestParam @NotBlank(message = "O termo de busca não pode estar vazio.") String termo) {
        return ResponseEntity.ok(lexicalSearchService.buscarPorTermo(termo));
    }
}
