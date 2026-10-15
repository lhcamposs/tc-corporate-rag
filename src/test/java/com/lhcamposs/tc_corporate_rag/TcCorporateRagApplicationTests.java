package com.lhcamposs.tc_corporate_rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifica apenas se o contexto Spring sobe (beans, injeção, configuração).
 * O PgVectorStore é substituído por um mock e o ChromaDB fica desabilitado
 * (autoconfigure excluído), então o teste não precisa de PostgreSQL, Chroma, Ollama nem Docker. As propriedades vêm de
 * src/test/resources/application.properties.
 */
@SpringBootTest
class TcCorporateRagApplicationTests {

    @MockitoBean
    private PgVectorStore pgVectorStore;

    @Test
    void contextLoads() {
    }
}
