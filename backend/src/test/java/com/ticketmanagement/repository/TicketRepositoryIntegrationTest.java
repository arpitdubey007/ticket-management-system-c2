package com.ticketmanagement.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.ticketmanagement.domain.Ticket;
import com.ticketmanagement.domain.TicketPriority;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketRepositoryIntegrationTest {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketListRepository ticketListRepository;

    @Autowired
    private PublicIdSequence publicIdSequence;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsWithDefaultsAndFindsByPublicId() {
        Ticket ticket = new Ticket(
                publicIdSequence.nextPublicId(),
                "Login fails",
                "Users cannot sign in on mobile.",
                "Access",
                TicketPriority.HIGH);
        ticket = ticketRepository.saveAndFlush(ticket);

        assertThat(ticket.getId()).isNotNull();
        assertThat(ticket.getVersion()).isZero();
        assertThat(ticket.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(ticketRepository.findByPublicId(ticket.getPublicId())).isPresent();
    }

    @Test
    void listsByUpdatedAtDescending() throws InterruptedException {
        Ticket older = ticketRepository.saveAndFlush(newTicket());
        Thread.sleep(5);
        Ticket newer = ticketRepository.saveAndFlush(newTicket());

        var page = ticketListRepository.findTickets(null, null, PageRequest.of(0, 10));
        assertThat(page.getContent().get(0).getId()).isEqualTo(newer.getId());
        assertThat(page.getContent()).extracting(Ticket::getId).contains(older.getId(), newer.getId());
    }

    @Test
    void sequenceStartsAt1001AndIncrements() {
        jdbcTemplate.execute("ALTER SEQUENCE ticket_public_seq RESTART WITH 1001");
        assertThat(publicIdSequence.nextPublicId()).isEqualTo("TKT-1001");
        assertThat(publicIdSequence.nextPublicId()).isEqualTo("TKT-1002");
    }

    private Ticket newTicket() {
        return new Ticket(
                publicIdSequence.nextPublicId(),
                "Title",
                "Description",
                "Billing",
                TicketPriority.MEDIUM);
    }
}