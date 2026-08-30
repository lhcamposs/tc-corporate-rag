package com.lhcamposs.tc_corporate_rag.dto;

/**
 * tempoRespostaMs é útil desde já para a Fase 4 (avaliação de custo
 * computacional pedida na proposta) — você já sai com esse dado registrado
 * em cada chamada, sem precisar instrumentar depois.
 */
public record QueryResponse(String resposta, long tempoRespostaMs) {
}
