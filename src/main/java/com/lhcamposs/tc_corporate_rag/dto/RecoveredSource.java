package com.lhcamposs.tc_corporate_rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.ai.document.Document;

import java.util.Map;

public record RecoveredSource(

        @Schema(description = "ID do chunk (UUID determinístico, igual no pgvector, no ChromaDB e na tabela lexical).",
                example = "4f1c2a9e-7b1d-3c55-8a0e-2d9f6b7a1c33")
        String vectorId,

        @Schema(description = "Arquivo (ou tabela) de origem do trecho.", example = "politica-ferias.pdf")
        String arquivo,

        @Schema(description = "Posição do chunk dentro da fonte (começa em 0).", example = "3")
        Integer chunkIndex,

        @Schema(description = "Texto do trecho recuperado.")
        String trecho,

        @Schema(description = "Relevância: similaridade (1 - distância cosseno) na busca vetorial; ts_rank na baseline lexical. "
                + "As escalas são diferentes e não devem ser comparadas diretamente.", example = "0.83")
        Double score
) {

    public static RecoveredSource of(Document documento) {
        Map<String, Object> metadata = documento.getMetadata();
        String arquivo = metadata.get("source_file") instanceof String s ? s : null;
        Integer chunkIndex = metadata.get("chunk_index") instanceof Number n ? n.intValue() : null;
        return new RecoveredSource(documento.getId(), arquivo, chunkIndex, documento.getText(), documento.getScore());
    }
}
