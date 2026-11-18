package com.ticketmanagement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TicketPriorityTest {

    @Test
    void parsesKnownPriorities() {
        assertThat(TicketPriority.fromValue("HIGH")).isEqualTo(TicketPriority.HIGH);
    }

    @Test
    void rejectsUnknownPriority() {
        assertThatThrownBy(() -> TicketPriority.fromValue("high"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CRITICAL");
    }
}