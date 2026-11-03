package com.lhcamposs.tc_corporate_rag.exceptions;

/** Entrada inválida nos endpoints de avaliação (método desconhecido, trecho inexistente no gabarito etc.). */
public class EvaluationInputException extends RuntimeException {
    public EvaluationInputException(String message) {
        super(message);
    }
}
