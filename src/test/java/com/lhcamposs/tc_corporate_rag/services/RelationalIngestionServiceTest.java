package com.lhcamposs.tc_corporate_rag.services;

import com.lhcamposs.tc_corporate_rag.exceptions.DocumentProcessingException;
import com.lhcamposs.tc_corporate_rag.exceptions.LexicalSearchException;
import com.lhcamposs.tc_corporate_rag.exceptions.LlmIntegrationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
    private VectorStore vectorStore;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private RelationalIngestionService relationalIngestionService;

    @Test
    @SuppressWarnings("unchecked")
    void ingestarTabela_DeveProcessarDocumentos_QuandoHouverDados() {

        Document doc1 = new Document("Categoria: Cat1\nTítulo: Tit1\n\nCont1", Map.of("record_id", 1L));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(doc1));

        int total = relationalIngestionService.ingestarTabela();

        assertEquals(1, total);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        Document indexado = captor.getValue().get(0);
        assertEquals(IngestionSupport.idDoChunk("artigo_conhecimento", 0), indexado.getId());
        assertEquals(doc1.getText(), indexado.getText());
        assertEquals(1L, indexado.getMetadata().get("record_id"));
        assertEquals("artigo_conhecimento", indexado.getMetadata().get("source_file"));
        assertEquals(0, indexado.getMetadata().get("chunk_index"));

        verify(jdbcTemplate).update(IngestionSupport.LEXICAL_UPSERT_SQL, "artigo_conhecimento", 0, doc1.getText());
        verify(jdbcTemplate).update(IngestionSupport.LEXICAL_DELETE_STALE_SQL, "artigo_conhecimento", 1);
        verify(vectorStore).delete(any(Filter.Expression.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestarTabela_DeveGerarOsMesmosIds_AoIngestarDuasVezes() {

        List<Document> origem = List.of(
                new Document("A", Map.of("record_id", 1L)),
                new Document("B", Map.of("record_id", 2L)));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(origem);

        relationalIngestionService.ingestarTabela();
        relationalIngestionService.ingestarTabela();

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(2)).add(captor.capture());
        List<String> idsPrimeira = captor.getAllValues().get(0).stream().map(Document::getId).toList();
        List<String> idsSegunda = captor.getAllValues().get(1).stream().map(Document::getId).toList();
        assertEquals(idsPrimeira, idsSegunda);
        assertEquals(2, idsPrimeira.stream().distinct().count());
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestarTabela_DeveEsvaziarOIndiceDaFonte_QuandoTabelaEstaVazia() {

        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(Collections.emptyList());

        int total = relationalIngestionService.ingestarTabela();

        assertEquals(0, total);
        verify(vectorStore, never()).add(anyList());
        verify(vectorStore).delete(any(Filter.Expression.class));
        verify(jdbcTemplate).update(IngestionSupport.LEXICAL_DELETE_STALE_SQL, "artigo_conhecimento", 0);
    }

    @Test
    void ingestarTabela_DeveLancarDocumentProcessingException_QuandoErroNaConsulta() {

        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenThrow(new RuntimeException("DB error"));

        assertThrows(DocumentProcessingException.class, () -> {
            relationalIngestionService.ingestarTabela();
        });
    }

    @Test
    void ingestarTabela_DeveLancarLlmIntegrationException_QuandoErroNoVectorStore() {

        List<Document> documentos = List.of(new Document("test"));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(documentos);
        doThrow(new RuntimeException("VectorStore error")).when(vectorStore).add(anyList());


        assertThrows(LlmIntegrationException.class, () -> {
            relationalIngestionService.ingestarTabela();
        });
    }

    @Test
    void ingestarTabela_DeveLancarLexicalSearchException_QuandoErroNoInsertRelacional() {

        List<Document> documentos = List.of(new Document("test"));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(documentos);
        when(jdbcTemplate.update(anyString(), any(), any(), any())).thenThrow(new RuntimeException("Insert error"));

        assertThrows(LexicalSearchException.class, () -> {
            relationalIngestionService.ingestarTabela();
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestarTabela_DeveMapearLinhaParaDocument_ComTextoEMetadadosDeOrigem() throws SQLException {

        ArgumentCaptor<RowMapper<Document>> mapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), mapperCaptor.capture())).thenReturn(Collections.emptyList());
        relationalIngestionService.ingestarTabela();

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(7L);
        when(rs.getString("category")).thenReturn("RH");
        when(rs.getString("title")).thenReturn("Politica de Ferias");
        when(rs.getString("content")).thenReturn("30 dias corridos.");

        Document documento = mapperCaptor.getValue().mapRow(rs, 0);

        assertEquals("Categoria: RH\nTítulo: Politica de Ferias\n\n30 dias corridos.", documento.getText());
        assertEquals("relational", documento.getMetadata().get("source"));
        assertEquals("artigo_conhecimento", documento.getMetadata().get("table"));
        assertEquals(7L, documento.getMetadata().get("record_id"));
        assertEquals("RH", documento.getMetadata().get("categoria"));
    }
}
