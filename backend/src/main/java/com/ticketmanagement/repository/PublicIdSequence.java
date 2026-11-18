package com.ticketmanagement.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PublicIdSequence {

    private final JdbcTemplate jdbcTemplate;

    public PublicIdSequence(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns the next public ticket number from {@code ticket_public_seq} (starts at 1001).
     */
    public long nextTicketNumber() {
        Long value = jdbcTemplate.queryForObject(
                "SELECT NEXT VALUE FOR ticket_public_seq",
                Long.class);
        if (value == null) {
            throw new IllegalStateException("ticket_public_seq returned null");
        }
        return value;
    }

    public String nextPublicId() {
        return "TKT-" + nextTicketNumber();
    }
}