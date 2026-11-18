package com.ticketmanagement.rag;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.ticketmanagement.domain.IngestionStatus;
import com.ticketmanagement.repository.TicketIngestionRepository;

@Component
public class IngestionMetrics {

    private final TicketIngestionRepository ticketIngestionRepository;

    public IngestionMetrics(TicketIngestionRepository ticketIngestionRepository) {
        this.ticketIngestionRepository = ticketIngestionRepository;
    }

    public long countIndexed() {
        return ticketIngestionRepository.countByStatus(IngestionStatus.INDEXED);
    }

    public long countPending() {
        return ticketIngestionRepository.countByStatus(IngestionStatus.PENDING);
    }

    public long countFailed() {
        return ticketIngestionRepository.countByStatus(IngestionStatus.FAILED);
    }

    public Duration oldestPendingAge() {
        Instant oldest = ticketIngestionRepository.findOldestPendingRequestedAt();
        if (oldest == null) {
            return Duration.ZERO;
        }
        return Duration.between(oldest, Instant.now());
    }
}