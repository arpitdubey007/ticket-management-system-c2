package com.ticketmanagement.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.ticketmanagement.domain.Comment;
import com.ticketmanagement.domain.Ticket;
import com.ticketmanagement.domain.TicketPriority;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommentRepositoryIntegrationTest {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PublicIdSequence publicIdSequence;

    private Ticket ticket;

    @BeforeEach
    void setUp() {
        ticket = ticketRepository.saveAndFlush(new Ticket(
                publicIdSequence.nextPublicId(),
                "Title",
                "Description",
                "Support",
                TicketPriority.LOW));
    }

    @Test
    void storesCommentsInChronologicalOrder() {
        Comment first = new Comment("Alice", "First");
        first.setTicket(ticket);
        commentRepository.saveAndFlush(first);

        Comment second = new Comment("Bob", "Second");
        second.setTicket(ticket);
        commentRepository.saveAndFlush(second);

        var loaded = commentRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId());
        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(0).getContent()).isEqualTo("First");
        assertThat(loaded.get(1).getContent()).isEqualTo("Second");
    }
}