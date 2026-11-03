package com.lhcamposs.tc_corporate_rag.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tcCorporateRagOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TC Corporate RAG API")
                        .description("Sistema de busca semântica e geração aumentada por recuperação (RAG) " +
                                "sobre bases de conhecimento corporativas — TCC do curso de Sistemas de Informação.")
                        .version("v0.0.1"));
    }
}

