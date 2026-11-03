package com.lhcamposs.tc_corporate_rag.vectorstore;

import com.lhcamposs.tc_corporate_rag.exceptions.VectorStoreNotAvailableException;

import java.util.Arrays;
import java.util.Locale;

/**
 * Bancos vetoriais comparados no objetivo específico (c) da proposta.
 * O nome (em minúsculas) é o valor aceito no parâmetro ?store= da API.
 */
public enum VectorStoreType {

    PGVECTOR("pgvector"),
    CHROMA("chroma");

    private final String nome;

    VectorStoreType(String nome) {
        this.nome = nome;
    }

    public String getNome() {
        return nome;
    }

    /** Converte o valor de ?store= (sem diferenciar maiúsculas). Em branco/nulo => pgvector. */
    public static VectorStoreType from(String valor) {
        if (valor == null || valor.isBlank()) {
            return PGVECTOR;
        }
        String normalizado = valor.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(tipo -> tipo.nome.equals(normalizado))
                .findFirst()
                .orElseThrow(() -> new VectorStoreNotAvailableException(
                        "Unknown vector store '" + valor + "'. Valid values: pgvector, chroma."));
    }
}
