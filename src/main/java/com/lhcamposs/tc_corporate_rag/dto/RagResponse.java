package com.lhcamposs.tc_corporate_rag.dto;

import java.util.List;

public record RagResponse(
        String resposta,
        List<RecoveredSource> fontes,
        long tempoRecuperacaoMs,
        long tempoGeracaoMs
) {
}
