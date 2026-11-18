package com.ticketmanagement.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class CitationValidatorTest {

    private final CitationValidator validator = new CitationValidator();

    @Test
    void acceptsCitationsPresentInRetrievedSet() {
        var result = validator.validate(
                "Payment failed due to retries [ticket:TKT-1001].",
                Set.of("TKT-1001", "TKT-1002"));
        assertThat(result.valid()).isTrue();
        assertThat(result.citedTicketIds()).containsExactly("TKT-1001");
    }

    @Test
    void rejectsInventedTicketId() {
        var result = validator.validate(
                "All fixed [ticket:TKT-9999].",
                Set.of("TKT-1001"));
        assertThat(result.valid()).isFalse();
        assertThat(result.rejectedTicketIds()).containsExactly("TKT-9999");
    }

    @Test
    void rejectsAnswerWithNoCitations() {
        var result = validator.validate("Payment issue resolved.", Set.of("TKT-1001"));
        assertThat(result.valid()).isFalse();
    }
}