package com.lhcamposs.tc_corporate_rag.services;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Limpeza do texto extraído de PDFs antes e depois do chunking.
 *
 * Por que existe: o extrator de PDF devolve o texto com centenas de espaços de
 * preenchimento (layout em colunas), caracteres invisíveis (espaço de largura
 * zero após marcadores de lista) e, nas quebras de página, fragmentos de uma
 * ou duas linhas que viram chunks inteiros. Isso polui o embedding e a busca
 * lexical e gera chunks sem informação suficiente para responder algo.
 *
 *  1. {@link #normalizar(String)}: roda ANTES de dividir o texto.
 *  2. {@link #unirChunksCurtos(List, int)}: roda DEPOIS de dividir e une cada
 *     chunk curto ao vizinho (o anterior; o primeiro vai para o seguinte).
 *
 * Não é usado na ingestão relacional: lá cada registro já é um chunk completo.
 */
final class PdfTextCleaner {

    static final int MIN_CHARS_CHUNK = 300;

    private PdfTextCleaner() {
    }

    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String limpo = texto
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\u200B\\u200C\\u200D\\u2060\\uFEFF\\u00AD]", "")
                .replace(' ', ' ');

        StringBuilder saida = new StringBuilder(limpo.length());
        for (String linha : limpo.split("\n", -1)) {
            saida.append(linha.replaceAll("[ \\t]+", " ").strip()).append('\n');
        }
        return saida.toString()
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    static List<String> unirCurtos(List<String> textos, int minChars) {
        List<String> resultado = new ArrayList<>();
        for (List<Integer> grupo : agrupar(textos, minChars)) {
            StringBuilder unido = new StringBuilder();
            for (int indice : grupo) {
                if (unido.length() > 0) {
                    unido.append('\n');
                }
                unido.append(textos.get(indice).strip());
            }
            resultado.add(unido.toString());
        }
        return resultado;
    }

    private static List<List<Integer>> agrupar(List<String> textos, int minChars) {
        List<List<Integer>> grupos = new ArrayList<>();
        List<Integer> tamanhos = new ArrayList<>();
        for (int i = 0; i < textos.size(); i++) {
            String atual = textos.get(i) == null ? "" : textos.get(i).strip();
            if (atual.isEmpty()) {
                continue;
            }
            if (atual.length() < minChars && !grupos.isEmpty()) {
                int ultimo = grupos.size() - 1;
                grupos.get(ultimo).add(i);
                tamanhos.set(ultimo, tamanhos.get(ultimo) + 1 + atual.length());
            } else {
                grupos.add(new ArrayList<>(List.of(i)));
                tamanhos.add(atual.length());
            }
        }
        if (grupos.size() > 1 && tamanhos.get(0) < minChars) {
            List<Integer> primeiro = grupos.remove(0);
            primeiro.addAll(grupos.get(0));
            grupos.set(0, primeiro);
        }
        return grupos;
    }

    static List<Document> normalizarDocumentos(List<Document> paginas) {
        List<Document> resultado = new ArrayList<>(paginas.size());
        for (Document pagina : paginas) {
            String texto = normalizar(pagina.getText());
            if (texto.isEmpty()) {
                continue;
            }
            resultado.add(Document.builder()
                    .text(texto)
                    .metadata(pagina.getMetadata())
                    .build());
        }
        return resultado;
    }

    static List<Document> unirChunksCurtos(List<Document> chunks, int minChars) {
        List<String> textos = new ArrayList<>(chunks.size());
        for (Document chunk : chunks) {
            textos.add(chunk.getText());
        }
        List<Document> resultado = new ArrayList<>();
        for (List<Integer> grupo : agrupar(textos, minChars)) {
            StringBuilder unido = new StringBuilder();
            for (int indice : grupo) {
                if (unido.length() > 0) {
                    unido.append('\n');
                }
                unido.append(textos.get(indice).strip());
            }
            resultado.add(Document.builder()
                    .text(unido.toString())
                    .metadata(chunks.get(grupo.get(0)).getMetadata())
                    .build());
        }
        return resultado;
    }
}
