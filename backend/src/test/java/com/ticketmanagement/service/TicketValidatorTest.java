package com.ticketmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketmanagement.config.TicketAppProperties;

class TicketValidatorTest {

    private TicketValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TicketValidator(new TicketAppProperties());
    }

    @Test
    void rejectsOversizedSearchTerm() {
        String term = "x".repeat(101);
        assertThat(validator.validateSearchTerm(term)).isNotEmpty();
    }

    @Test
    void rejectsUnknownStatusFilter() {
        assertThatThrownBy(() -> validator.rejectIfAny(validator.validateStatusFilter("DONE")))
                .isInstanceOf(RequestValidationException.class);
    }

    @Test
    void rejectsReservedAssignee() {
        assertThat(validator.validateOptionalAssignee("UNASSIGNED")).isNotEmpty();
        assertThat(validator.validateOptionalAssignee("unassigned")).isNotEmpty();
    }

    @Test
    void escapeLikeTreatsPercentLiterally() {
        String pattern = TicketValidator.escapeLikeLiteral("50% off");
        assertThat(pattern).contains("\\%");
    }
}