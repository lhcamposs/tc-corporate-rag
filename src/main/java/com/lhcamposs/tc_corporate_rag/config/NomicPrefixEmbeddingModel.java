package com.lhcamposs.tc_corporate_rag.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

/**
 * O nomic-embed-text exige um prefixo de tarefa no texto: "search_document: " nos
 * trechos indexados e "search_query: " nas perguntas (documentação da Nomic).
 * Nem o Ollama nem o Spring AI acrescentam esse prefixo, então este decorador o
 * insere antes de delegar ao modelo real.
 *
 * Como o Spring AI usa os métodos:
 *  - indexação (VectorStore.add): embed(List<Document>, ...) -> call(EmbeddingRequest) => prefixo de documento;
 *  - consulta (similaritySearch): embed(String) => prefixo de pergunta.
 */
public class NomicPrefixEmbeddingModel implements EmbeddingModel {

    static final String PREFIXO_DOCUMENTO = "search_document: ";
    static final String PREFIXO_PERGUNTA = "search_query: ";

    private final EmbeddingModel delegate;

    public NomicPrefixEmbeddingModel(EmbeddingModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> textos = request.getInstructions().stream().map(NomicPrefixEmbeddingModel::comoDocumento).toList();
        return delegate.call(new EmbeddingRequest(textos, request.getOptions()));
    }

    @Override
    public float[] embed(String texto) {
        return delegate.embed(comoPergunta(texto));
    }

    @Override
    public float[] embed(Document documento) {
        return delegate.embed(comoDocumento(documento.getText()));
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }

    static String comoDocumento(String texto) {
        return temPrefixo(texto) ? texto : PREFIXO_DOCUMENTO + texto;
    }

    static String comoPergunta(String texto) {
        return temPrefixo(texto) ? texto : PREFIXO_PERGUNTA + texto;
    }

    private static boolean temPrefixo(String texto) {
        return texto != null && (texto.startsWith(PREFIXO_DOCUMENTO) || texto.startsWith(PREFIXO_PERGUNTA));
    }
}
