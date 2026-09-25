package com.lhcamposs.tc_corporate_rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * tempoRespostaMs é útil desde já para a Fase 4 (avaliação de custo
 * computacional pedida na proposta) — você já sai com esse dado registrado
 * em cada chamada, sem precisar instrumentar depois.
 */
public record QueryResponse(

        @Schema(description = "Resposta gerada pelo LLM a partir do contexto recuperado.")
        String resposta,

        @Schema(description = "Tempo total da consulta (recuperação + geração), em milissegundos.", example = "842")
        long tempoRespostaMs
) {
}