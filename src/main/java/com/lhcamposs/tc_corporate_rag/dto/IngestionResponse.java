package com.lhcamposs.tc_corporate_rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record IngestionResponse(

        @Schema(description = "Nome original do arquivo PDF enviado.", example = "politica-ferias.pdf")
        String arquivo,

        @Schema(description = "Quantidade de chunks gerados e indexados no pgvector.", example = "14")
        int totalChunks,

        @Schema(description = "Status do processamento.", example = "PROCESSED")
        String status
) {
}
