package com.ticketmanagement.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.ticketmanagement.config.TestChatConfig.ScriptableTestChatModel;
import com.ticketmanagement.domain.IngestionStatus;
import com.ticketmanagement.rag.AskResult;
import com.ticketmanagement.rag.IngestionWorker;
import com.ticketmanagement.repository.TicketIngestionRepository;
import com.ticketmanagement.repository.TicketRepository;
import com.ticketmanagement.service.TicketService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AskApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private IngestionWorker ingestionWorker;

    @Autowired
    private TicketIngestionRepository ticketIngestionRepository;

    @Autowired
    private ScriptableTestChatModel chatModel;

    @Autowired
    private TicketRepository ticketRepository;

    @BeforeEach
    void resetState() {
        chatModel.reset();
        ticketIngestionRepository.deleteAll();
        ticketRepository.deleteAll();
    }

    @Test
    void relevantQuestionReturnsGroundedAnswer() throws Exception {
        var ticket = ticketService.createTicket(
                "Payment declined",
                "Customer sees code 402 on retry. Gateway retries exhausted the card.",
                "PAYMENT",
                "HIGH",
                null);
        awaitIngested(ticket.getId());

        chatModel.enqueue(
                "Previous failures were caused by gateway retries exhausting the card [ticket:"
                        + ticket.getPublicId()
                        + "].");

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"What caused payment failures with code 402 on %s?"}
                                """
                                .formatted(ticket.getPublicId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.citations[0].ticketId").value(ticket.getPublicId()))
                .andExpect(jsonPath("$.answer").value(
                        "Previous failures were caused by gateway retries exhausting the card [ticket:"
                                + ticket.getPublicId()
                                + "]."));
        assertThat(chatModel.getCallCount()).isEqualTo(1);
    }

    @Test
    void irrelevantQuestionReturnsNoMatchWithoutCallingChat() throws Exception {
        ticketService.createTicket(
                "Shipping delay", "Parcel held at customs.", "SHIPPING", "MEDIUM", null);

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"What is the capital of France?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(AskResult.NO_MATCH_ANSWER))
                .andExpect(jsonPath("$.grounded").value(false))
                .andExpect(jsonPath("$.citations").isEmpty());
        assertThat(chatModel.getCallCount()).isZero();
    }

    @Test
    void invalidCitationFromModelFailsClosed() throws Exception {
        var ticket = ticketService.createTicket(
                "Account locked", "User forgot password.", "ACCOUNT", "LOW", null);
        awaitIngested(ticket.getId());

        chatModel.enqueue("This is fixed now [ticket:TKT-9999].");

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Why was the account locked on %s?"}
                                """
                                .formatted(ticket.getPublicId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(AskResult.NO_MATCH_ANSWER))
                .andExpect(jsonPath("$.grounded").value(false))
                .andExpect(jsonPath("$.citations").isEmpty());
    }

    @Test
    void unsupportedAnswerWithoutCitationFailsClosed() throws Exception {
        var ticket = ticketService.createTicket(
                "Slow page", "Checkout spinner never stops.", "TECHNICAL", "MEDIUM", null);
        awaitIngested(ticket.getId());

        chatModel.enqueue("The payment page issue is fixed and customers are happy.");

        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Why is the payment page slow on %s?"}
                                """
                                .formatted(ticket.getPublicId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(AskResult.NO_MATCH_ANSWER))
                .andExpect(jsonPath("$.grounded").value(false));
    }

    @Test
    void emptyIndexReturnsNoMatch() throws Exception {
        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Any open tickets?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(AskResult.NO_MATCH_ANSWER))
                .andExpect(jsonPath("$.grounded").value(false))
                .andExpect(jsonPath("$.citations").isEmpty());
        assertThat(chatModel.getCallCount()).isZero();
    }

    @Test
    void blankQuestionReturns400WithoutCallingChat() throws Exception {
        mockMvc.perform(post("/api/ai/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(chatModel.getCallCount()).isZero();
    }

    private void awaitIngested(long ticketId) {
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            ingestionWorker.processDue();
            var row = ticketIngestionRepository.findByTicketId(ticketId).orElseThrow();
            assertThat(row.getStatus()).isEqualTo(IngestionStatus.INDEXED);
        });
    }
}