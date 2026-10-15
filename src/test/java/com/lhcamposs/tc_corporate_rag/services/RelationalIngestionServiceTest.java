package com.lhcamposs.tc_corporate_rag.services;

import com.lhcamposs.tc_corporate_rag.exceptions.DocumentProcessingException;
import com.lhcamposs.tc_corporate_rag.exceptions.LexicalSearchException;
import com.lhcamposs.tc_corporate_rag.exceptions.LlmIntegrationException;
import com.lhcamposs.tc_corporate_rag.vectorstore.VectorStoreRegistry;
import com.lhcamposs.tc_corporate_rag.vectorstore.VectorStoreType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelationalIngestionServiceTest {

    @Mock
    private VectorStore pgVectorStore;

    @Mock
    private VectorStore chromaVectorStore;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private RelationalIngestionService relationalIngestionService;

    @BeforeEach
    void setUp() {
        VectorStoreRegistry registry = new VectorStoreRegistry(Map.of(
                VectorStoreType.PGVECTOR, pgVectorStore,
                VectorStoreType.CHROMA, chromaVectorStore));
        relationalIngestionService = new RelationalIngestionService(registry, jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestarTabela_DeveProcessarDocumentos_QuandoHouverDados() {
        // Arrange
        Document doc1 = new Document("Categoria: Cat1\nTítulo: Tit1\n\nCont1", Map.of("record_id", 1L));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(doc1));

        // Act
        int total = relationalIngestionService.ingestarTabela();

        // Assert
        assertEquals(1, total);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(pgVectorStore).add(captor.capture());
        Document indexado = captor.getValue().get(0);
        String idEsperado = IngestionSupport.idDoChunk("artigo_conhecimento", 0);
        assertEquals(idEsperado, indexado.getId());
        assertEquals(doc1.getText(), indexado.getText());
        assertEquals(1L, indexado.getMetadata().get("record_id"));
        assertEquals("artigo_conhecimento", indexado.getMetadata().get("source_file"));
        assertEquals(0, indexado.getMetadata().get("chunk_index"));

        // O mesmo corpus (mesmos IDs) vai para os dois bancos vetoriais
        ArgumentCaptor<List<Document>> captorChroma = ArgumentCaptor.forClass(List.class);
        verify(chromaVectorStore).add(captorChroma.capture());
        assertEquals(idEsperado, captorChroma.getValue().get(0).getId());

        // Tabela lexical guarda o MESMO vector_id (rastreio por chunk)
        verify(jdbcTemplate).update(IngestionSupport.LEXICAL_UPSERT_SQL,
                "artigo_conhecimento", 0, idEsperado, doc1.getText());
        verify(jdbcTemplate).update(IngestionSupport.LEXICAL_DELETE_STALE_SQL, "artigo_conhecimento", 1);
        verify(pgVectorStore).delete(any(Filter.Expression.class));
        verify(chromaVectorStore).delete(any(Filter.Expression.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestarTabela_DeveGerarOsMesmosIds_AoIngestarDuasVezes() {
        // Arrange: a mesma base relacional é ingerida duas vezes
        List<Document> origem = List.of(
                new Document("A", Map.of("record_id", 1L)),
                new Document("B", Map.of("record_id", 2L)));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(origem);

        // Act
        relationalIngestionService.ingestarTabela();
        relationalIngestionService.ingestarTabela();

        // Assert: IDs idênticos => o upsert do banco vetorial atualiza em vez de duplicar
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(pgVectorStore, times(2)).add(captor.capture());
        List<String> idsPrimeira = captor.getAllValues().get(0).stream().map(Document::getId).toList();
        List<String> idsSegunda = captor.getAllValues().get(1).stream().map(Document::getId).toList();
        assertEquals(idsPrimeira, idsSegunda);
        assertEquals(2, idsPrimeira.stream().distinct().count());
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestarTabela_DeveEsvaziarOIndiceDaFonte_QuandoTabelaEstaVazia() {
        // Arrange
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(Collections.emptyList());

        // Act
        int total = relationalIngestionService.ingestarTabela();

        // Assert: nada a indexar, mas os chunks antigos da fonte são removidos nos dois bancos
        assertEquals(0, total);
        verify(pgVectorStore, never()).add(anyList());
        verify(chromaVectorStore, never()).add(anyList());
        verify(pgVectorStore).delete(any(Filter.Expression.class));
        verify(chromaVectorStore).delete(any(Filter.Expression.class));
        verify(jdbcTemplate).update(IngestionSupport.LEXICAL_DELETE_STALE_SQL, "artigo_conhecimento", 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestarTabela_DeveLancarDocumentProcessingException_QuandoErroNaConsulta() {
        // Arrange
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenThrow(new RuntimeException("DB error"));

        // Act & Assert
        assertThrows(DocumentProcessingException.class, () -> relationalIngestionService.ingestarTabela());
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestarTabela_DeveLancarLlmIntegrationException_QuandoErroNoVectorStore() {
        // Arrange
        List<Document> documentos = List.of(new Document("test"));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(documentos);
        doThrow(new RuntimeException("VectorStore error")).when(pgVectorStore).add(anyList());

        // Act & Assert
        LlmIntegrationException ex = assertThrows(LlmIntegrationException.class,
                () -> relationalIngestionService.ingestarTabela());
        assertTrue(ex.getMessage().contains("pgvector"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestarTabela_DeveIndicarOBancoQueFalhou_QuandoChromaFalha() {
        // Arrange
        List<Document> documentos = List.of(new Document("test"));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(documentos);
        doThrow(new RuntimeException("Chroma offline")).when(chromaVectorStore).add(anyList());

        // Act & Assert
        LlmIntegrationException ex = assertThrows(LlmIntegrationException.class,
                () -> relationalIngestionService.ingestarTabela());
        assertTrue(ex.getMessage().contains("chroma"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestarTabela_DeveLancarLexicalSearchException_QuandoErroNoInsertRelacional() {
        // Arrange
        List<Document> documentos = List.of(new Document("test"));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(documentos);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Insert error"));

        // Act & Assert
        assertThrows(LexicalSearchException.class, () -> relationalIngestionService.ingestarTabela());
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestarTabela_DeveMapearLinhaParaDocument_ComTextoEMetadadosDeOrigem() throws SQLException {
        // Arrange: captura o RowMapper que o service entrega ao JdbcTemplate
        ArgumentCaptor<RowMapper<Document>> mapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), mapperCaptor.capture())).thenReturn(Collections.emptyList());
        relationalIngestionService.ingestarTabela();

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(7L);
        when(rs.getString("category")).thenReturn("RH");
        when(rs.getString("title")).thenReturn("Politica de Ferias");
        when(rs.getString("content")).thenReturn("30 dias corridos.");

        // Act
        Document documento = mapperCaptor.getValue().mapRow(rs, 0);

        // Assert
        assertEquals("Categoria: RH\nTítulo: Politica de Ferias\n\n30 dias corridos.", documento.getText());
        assertEquals("relational", documento.getMetadata().get("source"));
        assertEquals("artigo_conhecimento", documento.getMetadata().get("table"));
        assertEquals(7L, documento.getMetadata().get("record_id"));
        assertEquals("RH", documento.getMetadata().get("categoria"));
    }
}
