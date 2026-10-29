package com.lhcamposs.tc_corporate_rag.controllers;

import com.lhcamposs.tc_corporate_rag.dto.QueryRequest;
import com.lhcamposs.tc_corporate_rag.dto.QueryResponse;
import com.lhcamposs.tc_corporate_rag.dto.RecoveredSource;
import com.lhcamposs.tc_corporate_rag.dto.RetrievalResponse;
import com.lhcamposs.tc_corporate_rag.dto.CeilingRequest;
import com.lhcamposs.tc_corporate_rag.services.EvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoints usados pelo script de avaliação (avaliacao/avaliar.py). Não fazem parte do uso normal do sistema. */
@RestController
@RequestMapping("/api/evaluation")
@Validated
@Tag(name = "Avaliação", description = "Endpoints de apoio ao experimento: recuperação isolada, teto do modelo e exportação de trechos")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @Operation(summary = "Recuperação isolada",
            description = "Devolve os k trechos mais relevantes no método escolhido (pgvector, chroma ou lexical), sem gerar resposta.")
    @PostMapping("/retrieve")
    public ResponseEntity<RetrievalResponse> recuperar(
            @Valid @RequestBody QueryRequest request,
            @Parameter(description = "pgvector, chroma ou lexical", example = "pgvector")
            @RequestParam(defaultValue = "pgvector") String method,
            @Parameter(description = "Quantidade de trechos devolvidos", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int k) {
        return ResponseEntity.ok(evaluationService.recuperar(request.pergunta(), method, k));
    }

    @Operation(summary = "Resposta com os trechos do gabarito (teto)",
            description = "Gera a resposta enviando ao modelo os trechos informados, no lugar dos recuperados. "
                    + "A nota média dessas respostas é a pontuação máxima relativa do modelo.")
    @PostMapping("/ceiling")
    public ResponseEntity<QueryResponse> ceiling(@Valid @RequestBody CeilingRequest request) {
        return ResponseEntity.ok(evaluationService.responderComGabarito(request.pergunta(), request.vectorIds()));
    }

    @Operation(summary = "Lista todos os trechos", description = "Exporta os trechos da base (vectorId, arquivo, posição e texto) para a anotação do gabarito.")
    @GetMapping("/chunks")
    public ResponseEntity<List<RecoveredSource>> chunks() {
        return ResponseEntity.ok(evaluationService.listarTrechos());
    }
}
