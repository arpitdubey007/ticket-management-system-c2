package com.ticketmanagement.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TicketIdExtractorTest {

    private final TicketIdExtractor extractor = new TicketIdExtractor();

    @Test
    void findsTicketIdsCaseInsensitive() {
        assertThat(extractor.extract("Please check tkt-1001 and TKT-2002"))
                .containsExactly("TKT-1001", "TKT-2002");
    }
}