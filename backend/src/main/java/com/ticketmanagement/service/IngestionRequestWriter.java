package com.ticketmanagement.service;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.ticketmanagement.domain.IngestionStatus;
import com.ticketmanagement.domain.Ticket;
import com.ticketmanagement.domain.TicketIngestion;
import com.ticketmanagement.rag.TicketChangedEvent;
import com.ticketmanagement.repository.TicketIngestionRepository;
import com.ticketmanagement.repository.TicketRepository;

@Component
public class IngestionRequestWriter {

    private final TicketIngestionRepository ticketIngestionRepository;
    private final TicketRepository ticketRepository;
    private final ApplicationEventPublisher eventPublisher;

    public IngestionRequestWriter(
            TicketIngestionRepository ticketIngestionRepository,
            TicketRepository ticketRepository,
            ApplicationEventPublisher eventPublisher) {
        this.ticketIngestionRepository = ticketIngestionRepository;
        this.ticketRepository = ticketRepository;
        this.eventPublisher = eventPublisher;
    }

    public void requestIngestion(Ticket ticket) {
        ticketRepository.flush();
        Instant now = Instant.now();
        long version = ticket.getVersion();
        TicketIngestion row = ticketIngestionRepository.findByTicketId(ticket.getId()).orElse(null);
        if (row == null) {
            ticketIngestionRepository.save(new TicketIngestion(ticket, version, now));
            eventPublisher.publishEvent(new TicketChangedEvent(ticket.getPublicId()));
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
        eventPublisher.publishEvent(new TicketChangedEvent(ticket.getPublicId()));
    }
}