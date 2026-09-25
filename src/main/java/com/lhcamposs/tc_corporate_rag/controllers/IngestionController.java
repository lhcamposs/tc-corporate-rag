package com.lhcamposs.tc_corporate_rag.controllers;

import com.lhcamposs.tc_corporate_rag.dto.IngestionResponse;
import com.lhcamposs.tc_corporate_rag.services.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Endpoints de ingestão de documentos corporativos (PDFs).
 */
@RestController
@RequestMapping("/api/documents")
@Tag(name = "Ingestão de Documentos",
        description = "Upload e processamento de PDFs (extração, chunking, embeddings e indexação no pgvector)")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * POST /api/documents/upload
     * Recebe um PDF (multipart/form-data, campo "arquivo") e o processa:
     * extrai texto, gera chunks, cria embeddings e indexa no pgvector.
     */
    @Operation(
            summary = "Envia um PDF para ingestão",
            description = "Extrai o texto do PDF, divide em chunks, gera embeddings e indexa no pgvector."
    )
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<IngestionResponse> upload(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        Resource resource = new InputStreamResource(arquivo.getInputStream());
        int totalChunks = ingestionService.ingestPdf(resource, arquivo.getOriginalFilename());

        return ResponseEntity.ok(new IngestionResponse(
                arquivo.getOriginalFilename(),
                totalChunks,
                "PROCESSED"
        ));
    }
}
