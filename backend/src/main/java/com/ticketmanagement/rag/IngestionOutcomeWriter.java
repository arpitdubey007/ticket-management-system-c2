package com.ticketmanagement.rag;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ticketmanagement.config.RagProperties;
import com.ticketmanagement.domain.IngestionStatus;
import com.ticketmanagement.domain.TicketIngestion;
import com.ticketmanagement.repository.TicketIngestionRepository;
import com.ticketmanagement.repository.TicketRepository;

@Component
public class IngestionOutcomeWriter {

    private final TicketIngestionRepository ticketIngestionRepository;
    private final TicketRepository ticketRepository;
    private final IngestionBackoff ingestionBackoff;
    private final RagProperties ragProperties;
    private final IndexReadiness indexReadiness;

    public IngestionOutcomeWriter(
            TicketIngestionRepository ticketIngestionRepository,
            TicketRepository ticketRepository,
            IngestionBackoff ingestionBackoff,
            RagProperties ragProperties,
            IndexReadiness indexReadiness) {
        this.ticketIngestionRepository = ticketIngestionRepository;
        this.ticketRepository = ticketRepository;
        this.ingestionBackoff = ingestionBackoff;
        this.ragProperties = ragProperties;
        this.indexReadiness = indexReadiness;
    }

    @Transactional
    public void commitSuccess(long ticketId, long indexedVersion) {
        TicketIngestion row = ticketIngestionRepository.findById(ticketId).orElseThrow();
        String publicId = ticketRepository.findById(ticketId).orElseThrow().getPublicId();
        row.setIndexedVersion(indexedVersion);
        row.setIndexedAt(Instant.now());
        row.setLastError(null);
        if (row.getRequestedVersion().equals(indexedVersion)) {
            row.setStatus(IngestionStatus.INDEXED);
            row.setNextAttemptAt(null);
        } else {
            row.setStatus(IngestionStatus.PENDING);
            row.setNextAttemptAt(Instant.now());
        }
        ticketIngestionRepository.save(row);
        indexReadiness.markIndexed(publicId);
    }

    @Transactional
    public void recordFailure(long ticketId, String error) {
        TicketIngestion managed = ticketIngestionRepository.findById(ticketId).orElseThrow();
        String publicId = ticketRepository.findById(ticketId).orElseThrow().getPublicId();
        int attempts = managed.getAttemptCount() + 1;
        managed.setAttemptCount(attempts);
        managed.setLastError(error);
        if (attempts >= ragProperties.getIngestion().getMaxAttempts()) {
            managed.setStatus(IngestionStatus.FAILED);
            managed.setNextAttemptAt(null);
            indexReadiness.markFailed(publicId);
        } else {
            managed.setStatus(IngestionStatus.PENDING);
            managed.setNextAttemptAt(ingestionBackoff.nextAttemptAfter(attempts));
        }
        ticketIngestionRepository.save(managed);
    }
}