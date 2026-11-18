package com.ticketmanagement.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiParityTest {

    private static final Set<String> REQUIRED_PATHS = Set.of(
            "/api/tickets",
            "/api/tickets/{publicId}",
            "/api/tickets/{publicId}/status",
            "/api/tickets/{publicId}/comments",
            "/api/ai/ask");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void documentsContractPaths() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        for (String path : REQUIRED_PATHS) {
            assertThat(body).contains("\"" + path + "\"");
        }
    }
}