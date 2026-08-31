package com.lhcamposs.tc_corporate_rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração central do cliente de chat (ChatClient) usado no pipeline RAG.
 * <p>
 * O ChatClient é montado com o QuestionAnswerAdvisor, que automaticamente:
 * 1. Recebe a pergunta do usuário
 * 2. Busca os trechos mais relevantes no VectorStore (pgvector)
 * 3. Injeta esses trechos como contexto no prompt enviado ao LLM (Ollama)
 * 4. Retorna a resposta gerada com base apenas no contexto recuperado
 * <p>
 * NOTA: a partir do Spring AI 1.0.3, o QuestionAnswerAdvisor mudou de pacote:
 * antes ficava em org.springframework.ai.chat.client.advisor, agora está em
 * org.springframework.ai.chat.client.advisor.vectorstore. Se você estiver
 * usando uma versão de milestone antiga (M3/M4), o import volta a ser o
 * pacote sem ".vectorstore" no final.
 */
@Configuration
public class ChatConfig {

    private static final String SYSTEM_PROMPT = """
            You are an assistant that answers questions EXCLUSIVELY based
            on the context of the provided corporate documents.\s
            If the answer is not in the context, clearly state that you
            did not find that information in the knowledge base — do not use
            knowledge outside of the provided context.
            """;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, VectorStore vectorStore) {
        QuestionAnswerAdvisor questionAnswerAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .build();

        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(questionAnswerAdvisor)
                .build();
    }
}
