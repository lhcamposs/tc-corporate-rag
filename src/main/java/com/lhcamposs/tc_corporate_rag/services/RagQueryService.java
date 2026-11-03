package com.lhcamposs.tc_corporate_rag.services;

import com.lhcamposs.tc_corporate_rag.dto.RagResponse;
import com.lhcamposs.tc_corporate_rag.dto.RecoveredSource;
import com.lhcamposs.tc_corporate_rag.exceptions.LlmIntegrationException;
import com.lhcamposs.tc_corporate_rag.vectorstore.VectorStoreRegistry;
import com.lhcamposs.tc_corporate_rag.vectorstore.VectorStoreType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Executa a consulta RAG em duas etapas MEDIDAS SEPARADAMENTE:
 *  1. Recuperação: busca vetorial no banco escolhido (pgvector ou ChromaDB).
 *  2. Geração: o LLM responde usando só os trechos recuperados como contexto.
 *
 * Fazer a recuperação aqui (em vez de dentro de um advisor) permite escolher o
 * banco por requisição, devolver os trechos usados como fontes e isolar o tempo
 * de cada etapa, o que é essencial para comparar pgvector x ChromaDB.
 */
@Service
public class RagQueryService {

    static final String SYSTEM_PROMPT = """
            You are an assistant that answers questions EXCLUSIVELY based
            on the context of the provided corporate documents.
            If the answer is not in the context, clearly state that you
            did not find that information in the knowledge base — do not use
            knowledge outside of the provided context.
            Always answer in Brazilian Portuguese. When possible, cite the sources
            using their numbers, like [1].
            """;

    static final String SEM_CONTEXTO = "(no relevant passage was retrieved from the knowledge base)";

    private final ChatClient chatClient;
    private final VectorStoreRegistry vectorStoreRegistry;
    private final int topK;
    private final double similarityThreshold;

    private final double temperatura;

    public RagQueryService(ChatClient chatClient,
                           VectorStoreRegistry vectorStoreRegistry,
                           @Value("${rag.top-k:4}") int topK,
                           @Value("${rag.similarity-threshold:0.0}") double similarityThreshold,
                           @Value("${rag.temperatura:0.0}") double temperatura) {
        this.chatClient = chatClient;
        this.vectorStoreRegistry = vectorStoreRegistry;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.temperatura = temperatura;
    }

    public RagResponse responder(String pergunta, VectorStoreType tipoStore) {
        // 1. Recuperação
        long inicioRecuperacao = System.nanoTime();
        List<Document> documentos = recuperar(pergunta, tipoStore, topK);
        long fimRecuperacao = System.nanoTime();

        // 2. Geração
        String response = gerar(pergunta, documentos);
        long fimGeracao = System.nanoTime();

        List<RecoveredSource> fontes = documentos.stream().map(RecoveredSource::of).toList();
        return new RagResponse(response, fontes,
                (fimRecuperacao - inicioRecuperacao) / 1_000_000,
                (fimGeracao - fimRecuperacao) / 1_000_000);
    }

    public List<Document> recuperar(String pergunta, VectorStoreType tipoStore, int k) {
        // Lança VectorStoreNotAvailableException (400) se o banco não estiver habilitado
        VectorStore vectorStore = vectorStoreRegistry.get(tipoStore);
        List<Document> documentos;
        try {
            documentos = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(pergunta)
                    .topK(k)
                    .similarityThreshold(similarityThreshold)
                    .build());
        } catch (Exception e) {
            throw new LlmIntegrationException(
                    "Failed to retrieve context from the vector store '" + tipoStore.getNome()
                            + "'. Check if the store and the embedding model (e.g., Ollama) are running.", e);
        }
        return documentos == null ? List.of() : documentos;
    }

    public String gerar(String pergunta, List<Document> documentos) {
        try {
            Prompt prompt = new Prompt(List.of(
                    SystemMessage.builder().text(SYSTEM_PROMPT).build(),
                    UserMessage.builder().text(montarMensagemDoUsuario(pergunta, documentos)).build()),
                    ChatOptions.builder().temperature(temperatura).build());
            return chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            throw new LlmIntegrationException(
                    "Failed to generate a response using the language model. Check if the service (e.g., Ollama) is running and accessible.",
                    e
            );
        }
    }

    static String montarMensagemDoUsuario(String pergunta, List<Document> documentos) {
        StringBuilder contexto = new StringBuilder();
        if (documentos.isEmpty()) {
            contexto.append(SEM_CONTEXTO).append('\n');
        }
        for (int i = 0; i < documentos.size(); i++) {
            RecoveredSource fonte = RecoveredSource.of(documentos.get(i));
            contexto.append('[').append(i + 1).append("] ")
                    .append(fonte.arquivo() != null ? fonte.arquivo() : "unknown source");
            if (fonte.chunkIndex() != null) {
                contexto.append(" (chunk ").append(fonte.chunkIndex()).append(')');
            }
            contexto.append('\n').append(fonte.trecho()).append("\n\n");
        }
        return "Context:\n" + contexto + "Question: " + pergunta;
    }
}
