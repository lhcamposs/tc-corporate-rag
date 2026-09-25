package com.lhcamposs.tc_corporate_rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RelationalIngestionResponse(

        @Schema(description = "Tabela relacional que foi consultada e indexada.", example = "artigo_conhecimento")
        String tabela,

        @Schema(description = "Quantidade de registros lidos e indexados no pgvector.", example = "6")
        int totalRegistros,

        @Schema(description = "Status do processamento.", example = "PROCESSED")
        String status
) {
}
