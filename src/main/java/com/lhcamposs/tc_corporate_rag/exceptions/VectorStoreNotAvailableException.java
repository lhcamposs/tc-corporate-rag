package com.lhcamposs.tc_corporate_rag.exceptions;

/** Banco vetorial desconhecido (valor inválido em ?store=) ou não habilitado nesta execução. */
public class VectorStoreNotAvailableException extends RuntimeException {
    public VectorStoreNotAvailableException(String message) {
        super(message);
    }
}
