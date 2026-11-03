package com.lhcamposs.tc_corporate_rag.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Modelo de embeddings usado pelos dois bancos vetoriais. Com rag.embedding.prefixos=true
 * (padrão), aplica os prefixos de tarefa do nomic-embed-text; com false, usa o modelo
 * sem prefixo (configuração da primeira rodada do experimento).
 * Ao mudar a propriedade, é preciso regerar os vetores (POST /api/evaluation/reindex).
 */
@Configuration
public class EmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingModel embeddingModelDoRag(OllamaEmbeddingModel ollamaEmbeddingModel,
                                              @Value("${rag.embedding.prefixos:true}") boolean usarPrefixos) {
        return usarPrefixos ? new NomicPrefixEmbeddingModel(ollamaEmbeddingModel) : ollamaEmbeddingModel;
    }
}
