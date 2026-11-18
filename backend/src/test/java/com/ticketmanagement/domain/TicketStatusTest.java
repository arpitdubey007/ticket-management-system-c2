package com.ticketmanagement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TicketStatusTest {

    @Test
    void parsesKnownStatuses() {
        assertThat(TicketStatus.fromValue("OPEN")).isEqualTo(TicketStatus.OPEN);
        assertThat(TicketStatus.fromValue("IN_PROGRESS")).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void rejectsUnknownStatus() {
        assertThatThrownBy(() -> TicketStatus.fromValue("open"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OPEN");
    }

    @Test
    void terminalStatuses() {
        assertThat(TicketStatus.CLOSED.isTerminal()).isTrue();
        assertThat(TicketStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(TicketStatus.OPEN.isTerminal()).isFalse();
    }
}