package com.ticketmanagement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TicketEntityTest {

    @Test
    void trimsRequiredFieldsAndDefaults() {
        Ticket ticket = new Ticket(
                "TKT-1001",
                "  Title  ",
                "  Description  ",
                "  Billing  ",
                null);

        assertThat(ticket.getTitle()).isEqualTo("Title");
        assertThat(ticket.getDescription()).isEqualTo("Description");
        assertThat(ticket.getCategory()).isEqualTo("Billing");
        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void blankAssigneeBecomesNull() {
        Ticket ticket = sample();
        ticket.setAssignee("   ");
        assertThat(ticket.getAssignee()).isNull();
    }

    @Test
    void rejectsBlankTitle() {
        Ticket ticket = sample();
        assertThatThrownBy(() -> ticket.setTitle("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void commentTrimsAuthorAndContent() {
        Comment comment = new Comment("  Alice  ", "  Hello  ");
        assertThat(comment.getAuthor()).isEqualTo("Alice");
        assertThat(comment.getContent()).isEqualTo("Hello");
    }

    private static Ticket sample() {
        return new Ticket("TKT-1001", "Title", "Description", "Billing", TicketPriority.MEDIUM);
    }
}