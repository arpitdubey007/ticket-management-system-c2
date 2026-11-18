package com.ticketmanagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI ticketManagementOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Support Ticket Management API")
                        .description("REST API for tickets and POST /api/ai/ask — see spec/api-contract.md")
                        .version("0.0.1"));
    }
}