package com.lhcamposs.tc_corporate_rag.dto;

public record IngestionResponse(String arquivo, int totalChunks, String status) {
}
