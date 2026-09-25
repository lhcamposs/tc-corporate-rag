package com.lhcamposs.tc_corporate_rag.controllers;

import com.lhcamposs.tc_corporate_rag.dto.RelationalIngestionResponse;
import com.lhcamposs.tc_corporate_rag.services.RelationalIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/relational")
@Tag(name = "Ingestão Relacional", description = "Ingestão de registros de uma base relacional já existente (ex.: wiki interna, FAQs) para o RAG")
public class RelationalIngestionController {

    private final RelationalIngestionService relationalIngestionService;

    public RelationalIngestionController(RelationalIngestionService relationalIngestionService) {
        this.relationalIngestionService = relationalIngestionService;
    }

    /**
     * POST /api/relational/ingest
     * Lê todos os registros da tabela artigo_conhecimento, gera embeddings
     * e indexa no pgvector (além de salvar em document_chunk, para a
     * baseline lexical).
     */
    @Operation(
            summary = "Ingesta a base relacional",
            description = "Lê os registros da tabela artigo_conhecimento, gera embeddings e indexa no pgvector."
    )
    @PostMapping("/ingest")
    public ResponseEntity<RelationalIngestionResponse> ingest() {
        int total = relationalIngestionService.ingestarTabela();

        return ResponseEntity.ok(new RelationalIngestionResponse(
                "artigo_conhecimento",
                total,
                "PROCESSED"
        ));
    }
}
