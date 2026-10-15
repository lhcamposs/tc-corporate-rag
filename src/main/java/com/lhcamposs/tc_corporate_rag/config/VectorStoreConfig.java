package com.lhcamposs.tc_corporate_rag.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Para ter pgvector E ChromaDB na mesma aplicação (objetivo c), os dois
 * autoconfigures não podem coexistir: ambos registram um bean chamado
 * "vectorStore" e o Spring falha na subida.
 *
 * Solução: o autoconfigure do pgvector é excluído (ver
 * spring.autoconfigure.exclude no application.properties) e o PgVectorStore é
 * montado aqui, lendo as MESMAS propriedades spring.ai.vectorstore.pgvector.*
 * de antes. O ChromaDB continua 100% por autoconfigure
 * (spring.ai.vectorstore.chroma.*).
 *
 * A escolha entre os dois em tempo de execução é feita pelo VectorStoreRegistry.
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    public PgVectorStore pgVectorStore(
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.pgvector.dimensions:768}") int dimensions,
            @Value("${spring.ai.vectorstore.pgvector.distance-type:COSINE_DISTANCE}")
            PgVectorStore.PgDistanceType distanceType,
            @Value("${spring.ai.vectorstore.pgvector.index-type:HNSW}")
            PgVectorStore.PgIndexType indexType,
            @Value("${spring.ai.vectorstore.pgvector.initialize-schema:true}") boolean initializeSchema) {

        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(dimensions)
                .distanceType(distanceType)
                .indexType(indexType)
                .initializeSchema(initializeSchema)
                .build();
    }
}
