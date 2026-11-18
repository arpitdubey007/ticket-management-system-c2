package com.ticketmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.ticketmanagement.domain.TicketStatus;
import com.ticketmanagement.repository.TicketIngestionRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketServiceIntegrationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketStatusService ticketStatusService;

    @Autowired
    private TicketIngestionRepository ticketIngestionRepository;

    @Test
    void createWritesIngestionRow() {
        var ticket = ticketService.createTicket("Title", "Body", "PAYMENT", null, null);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticketIngestionRepository.findByTicketId(ticket.getId())).isPresent();
    }

    @Test
    void rejectedTransitionDoesNotBumpVersionOrIngestion() {
        var ticket = ticketService.createTicket("T", "D", "OTHER", null, null);
        ticket = ticketStatusService.changeStatus(ticket.getPublicId(), "IN_PROGRESS", null);
        long version = ticket.getVersion();
        try {
            ticketStatusService.changeStatus(ticket.getPublicId(), "RESOLVED", "  ");
        } catch (RequestValidationException ignored) {
        }
        var reloaded = ticketService.getTicket(ticket.getPublicId());
        assertThat(reloaded.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(reloaded.getVersion()).isEqualTo(version);
    }
}