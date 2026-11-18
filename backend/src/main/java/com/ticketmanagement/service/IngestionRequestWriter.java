package com.ticketmanagement.service;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.ticketmanagement.domain.IngestionStatus;
import com.ticketmanagement.domain.Ticket;
import com.ticketmanagement.domain.TicketIngestion;
import com.ticketmanagement.repository.TicketIngestionRepository;

@Component
public class IngestionRequestWriter {

    private final TicketIngestionRepository ticketIngestionRepository;

    public IngestionRequestWriter(TicketIngestionRepository ticketIngestionRepository) {
        this.ticketIngestionRepository = ticketIngestionRepository;
    }

    public void requestIngestion(Ticket ticket) {
        Instant now = Instant.now();
        long version = ticket.getVersion();
        TicketIngestion row = ticketIngestionRepository.findByTicketId(ticket.getId()).orElse(null);
        if (row == null) {
            ticketIngestionRepository.save(new TicketIngestion(ticket, version, now));
            return;
        }
        IngestionStatus previous = row.getStatus();
        row.setRequestedVersion(version);
        row.setStatus(IngestionStatus.PENDING);
        row.setAttemptCount(0);
        row.setNextAttemptAt(now);
        row.setLastError(null);
        if (previous == IngestionStatus.INDEXED) {
            row.setRequestedAt(now);
        }
        ticketIngestionRepository.save(row);
    }
}