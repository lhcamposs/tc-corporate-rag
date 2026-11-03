package com.lhcamposs.tc_corporate_rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record RetrievalResponse(

        @Schema(description = "Método de recuperação: pgvector, chroma ou lexical.", example = "pgvector")
        String metodo,

        @Schema(description = "Trechos devolvidos, em ordem de relevância.")
        List<RecoveredSource> fontes,

        @Schema(description = "Tempo da recuperação (inclui o embedding da pergunta na busca vetorial), em milissegundos.",
                example = "45")
        long tempoRecuperacaoMs
) {
}
