package com.lhcamposs.tc_corporate_rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record QueryResponse(

        @Schema(description = "Resposta gerada pelo LLM a partir do contexto recuperado.")
        String resposta,

        @Schema(description = "Banco vetorial usado na recuperação.", example = "pgvector")
        String store,

        @Schema(description = "Trechos recuperados e enviados ao LLM como contexto (as fontes da resposta).")
        List<RecoveredSource> fontes,

        @Schema(description = "Tempo da busca vetorial (inclui o embedding da pergunta), em milissegundos.", example = "120")
        long tempoRecuperacaoMs,

        @Schema(description = "Tempo de geração da resposta pelo LLM, em milissegundos.", example = "720")
        long tempoGeracaoMs,

        @Schema(description = "Tempo total da consulta, em milissegundos.", example = "842")
        long tempoRespostaMs
) {
}
