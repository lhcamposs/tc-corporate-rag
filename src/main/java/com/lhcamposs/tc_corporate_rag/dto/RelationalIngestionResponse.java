package com.lhcamposs.tc_corporate_rag.dto;

public record RelationalIngestionResponse(
        String tabela,
        int totalRegistros,
        String status
) {}
