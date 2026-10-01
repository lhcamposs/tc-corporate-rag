package com.lhcamposs.tc_corporate_rag.services;

import com.lhcamposs.tc_corporate_rag.exceptions.DocumentProcessingException;
import com.lhcamposs.tc_corporate_rag.exceptions.LexicalSearchException;
import com.lhcamposs.tc_corporate_rag.exceptions.LlmIntegrationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
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
    void ingestarTabela_DeveProcessarDocumentos_QuandoHouverDados() {
        Document doc1 = new Document("Categoria: Cat1\nTítulo: Tit1\n\nCont1", Map.of("record_id", 1L));
        List<Document> documentos = List.of(doc1);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(documentos);

        int total = relationalIngestionService.ingestarTabela();

        assertEquals(1, total);
        verify(vectorStore).add(documentos);
        verify(jdbcTemplate).update(anyString(), eq("artigo_conhecimento"), eq(0), eq(doc1.getText()));
    }

    @Test
    void ingestarTabela_DeveRetornarZero_QuandoNaoHouverDados() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(Collections.emptyList());

        int total = relationalIngestionService.ingestarTabela();

        assertEquals(0, total);
        verifyNoInteractions(vectorStore);
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
