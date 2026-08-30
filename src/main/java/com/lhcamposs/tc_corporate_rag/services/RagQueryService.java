package com.lhcamposs.tc_corporate_rag.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Executa a consulta RAG: pergunta em linguagem natural -> resposta
 * fundamentada nos documentos recuperados.
 *
 * O ChatClient já vem configurado (ver ChatConfig) com o QuestionAnswerAdvisor,
 * que cuida sozinho da busca vetorial no pgvector e da montagem do prompt com
 * o contexto recuperado — aqui só chamamos o cliente.
 */
@Service
public class RagQueryService {

    private final ChatClient chatClient;

    public RagQueryService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String responder(String pergunta) {
        return chatClient.prompt()
                .user(pergunta)
                .call()
                .content();
    }
}
