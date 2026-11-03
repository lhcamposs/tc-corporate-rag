package com.lhcamposs.tc_corporate_rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QueryRequest(
        @Schema(
                description = "Pergunta em linguagem natural sobre a base de conhecimento corporativa.",
                example = "Quantos dias de férias eu tenho direito?"
        )
        @NotBlank(message = "A pergunta não pode estar vazia.")
        @Size(max = 2000, message = "A pergunta deve ter no máximo 2000 caracteres.")
        String pergunta
) {
}
