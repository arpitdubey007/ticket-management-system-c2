package com.ticketmanagement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TicketStateMachineTest {

    @Test
    void allowsOnlyDefinedTransitions() {
        assertThat(TicketStateMachine.isAllowed(TicketStatus.OPEN, TicketStatus.IN_PROGRESS)).isTrue();
        assertThat(TicketStateMachine.isAllowed(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED)).isTrue();
        assertThat(TicketStateMachine.isAllowed(TicketStatus.RESOLVED, TicketStatus.CLOSED)).isTrue();
        assertThat(TicketStateMachine.isAllowed(TicketStatus.CLOSED, TicketStatus.OPEN)).isFalse();
        assertThat(TicketStateMachine.isAllowed(TicketStatus.RESOLVED, TicketStatus.OPEN)).isFalse();
        assertThat(TicketStateMachine.isAllowed(TicketStatus.CANCELLED, TicketStatus.OPEN)).isFalse();
    }

    @Test
    void rejectsSameStatus() {
        for (TicketStatus status : TicketStatus.values()) {
            assertThatThrownBy(() -> TicketStateMachine.validateTransition(status, status))
                    .isInstanceOf(InvalidTransitionException.class);
        }
    }

    @Test
    void fullMatrixHasTwentyRejectsAndFiveAllows() {
        int allowed = 0;
        int rejected = 0;
        for (TicketStatus from : TicketStatus.values()) {
            for (TicketStatus to : TicketStatus.values()) {
                if (TicketStateMachine.isAllowed(from, to)) {
                    allowed++;
                } else {
                    rejected++;
                }
            }
        }
        assertThat(allowed).isEqualTo(5);
        assertThat(rejected).isEqualTo(20);
    }
}