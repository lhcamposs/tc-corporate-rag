package com.lhcamposs.tc_corporate_rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CeilingRequest(

        @Schema(description = "Pergunta em linguagem natural.", example = "Quantos dias de férias tem um docente EBTT?")
        @NotBlank(message = "A pergunta não pode estar vazia.")
        @Size(max = 2000, message = "A pergunta deve ter no máximo 2000 caracteres.")
        String pergunta,

        @Schema(description = "vectorIds dos trechos corretos (gabarito), na ordem em que serão enviados ao modelo.")
        @NotEmpty(message = "Informe ao menos um vectorId do gabarito.")
        List<@NotBlank String> vectorIds
) {
}
