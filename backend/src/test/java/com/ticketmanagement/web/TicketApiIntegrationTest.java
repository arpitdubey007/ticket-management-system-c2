package com.ticketmanagement.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createListGetUpdateAndSearch() throws Exception {
        String ticketId = createTicket("Payment declined", "Card fails", "PAYMENT", "HIGH");

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].ticketId").value(ticketId));

        mockMvc.perform(get("/api/tickets/{id}", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedTransitions").isArray())
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(put("/api/tickets/{id}", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Payment declined (updated)","priority":"CRITICAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Payment declined (updated)"));

        mockMvc.perform(get("/api/tickets").param("q", "declined").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void invalidTransitionReturns400() throws Exception {
        String ticketId = createTicket("T", "D", "OTHER", null);
        transition(ticketId, "IN_PROGRESS");
        transition(ticketId, "CANCELLED");

        mockMvc.perform(post("/api/tickets/{id}/status", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
    }

    @Test
    void resolveRequiresNotes() throws Exception {
        String ticketId = createTicket("Bug", "Details", "TECHNICAL", null);
        transition(ticketId, "IN_PROGRESS");

        mockMvc.perform(post("/api/tickets/{id}/status", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void validLifecycleToClosed() throws Exception {
        String ticketId = createTicket("Bug", "Details", "TECHNICAL", null);
        transition(ticketId, "IN_PROGRESS");
        transition(ticketId, "RESOLVED", "Fixed config");
        transition(ticketId, "CLOSED");

        mockMvc.perform(get("/api/tickets/{id}", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.resolutionNotes").value("Fixed config"));
    }

    @Test
    void terminalTicketUpdateIs409() throws Exception {
        String ticketId = createTicket("Bug", "Details", "TECHNICAL", null);
        transition(ticketId, "IN_PROGRESS");
        transition(ticketId, "CANCELLED");

        mockMvc.perform(put("/api/tickets/{id}", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nope\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void addCommentPersists() throws Exception {
        String ticketId = createTicket("Bug", "Details", "TECHNICAL", null);
        mockMvc.perform(post("/api/tickets/{id}/comments", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"Ada\",\"content\":\"Seen before\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value("Ada"));

        mockMvc.perform(get("/api/tickets/{id}", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments[0].content").value("Seen before"));
    }

    @Test
    void unknownTicketIs404() throws Exception {
        mockMvc.perform(get("/api/tickets/TKT-9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void malformedPublicIdIs400() throws Exception {
        mockMvc.perform(get("/api/tickets/not-an-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String createTicket(String title, String description, String category, String priority)
            throws Exception {
        StringBuilder json = new StringBuilder("{");
        json.append("\"title\":\"").append(title).append("\",");
        json.append("\"description\":\"").append(description).append("\",");
        json.append("\"category\":\"").append(category).append("\"");
        if (priority != null) {
            json.append(",\"priority\":\"").append(priority).append("\"");
        }
        json.append("}");
        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.toString()))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String id = body.get("ticketId").asText();
        assertThat(id).startsWith("TKT-");
        return id;
    }

    private void transition(String ticketId, String status) throws Exception {
        transition(ticketId, status, null);
    }

    private void transition(String ticketId, String status, String notes) throws Exception {
        StringBuilder json = new StringBuilder("{\"status\":\"").append(status).append("\"");
        if (notes != null) {
            json.append(",\"resolutionNotes\":\"").append(notes).append("\"");
        }
        json.append("}");
        mockMvc.perform(post("/api/tickets/{id}/status", ticketId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.toString()))
                .andExpect(status().isOk());
    }
}