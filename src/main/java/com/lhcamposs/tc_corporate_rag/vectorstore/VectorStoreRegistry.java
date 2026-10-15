package com.lhcamposs.tc_corporate_rag.vectorstore;

import com.lhcamposs.tc_corporate_rag.exceptions.VectorStoreNotAvailableException;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Ponto único de acesso aos bancos vetoriais (objetivo c: pgvector x ChromaDB).
 *
 * - Consulta (RAG): o chamador escolhe o banco com ?store=.
 * - Ingestão: grava em TODOS os bancos disponíveis, com os mesmos IDs, para que
 *   os dois armazenem exatamente o mesmo corpus e a comparação seja justa.
 *
 * Os bancos são identificados pela classe concreta do bean (e não pelo nome do
 * bean), então funciona mesmo que o autoconfigure mude os nomes.
 */
@Component
public class VectorStoreRegistry {

    private final Map<VectorStoreType, VectorStore> stores = new EnumMap<>(VectorStoreType.class);

    @Autowired
    public VectorStoreRegistry(List<VectorStore> beans) {
        for (VectorStore bean : beans) {
            if (bean instanceof PgVectorStore) {
                stores.putIfAbsent(VectorStoreType.PGVECTOR, bean);
            } else if (bean instanceof ChromaVectorStore) {
                stores.putIfAbsent(VectorStoreType.CHROMA, bean);
            }
        }
    }

    /** Construtor explícito (testes e montagem manual). */
    public VectorStoreRegistry(Map<VectorStoreType, VectorStore> stores) {
        this.stores.putAll(stores);
    }

    public VectorStore get(VectorStoreType tipo) {
        VectorStore store = stores.get(tipo);
        if (store == null) {
            throw new VectorStoreNotAvailableException(
                    "Vector store '" + tipo.getNome() + "' is not enabled in this environment. Available: "
                            + stores.keySet().stream().map(VectorStoreType::getNome).toList());
        }
        return store;
    }

    /** Todos os bancos disponíveis, em ordem estável (pgvector, chroma). */
    public Map<VectorStoreType, VectorStore> all() {
        return Collections.unmodifiableMap(stores);
    }
}
