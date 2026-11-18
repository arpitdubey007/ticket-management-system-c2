package com.ticketmanagement.rag;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ticketmanagement.domain.Ticket;
import com.ticketmanagement.domain.TicketIngestion;
import com.ticketmanagement.repository.TicketIngestionRepository;
import com.ticketmanagement.repository.TicketRepository;
import com.ticketmanagement.service.IngestionRequestWriter;

@Component
public class IndexReconciler {

    private static final Logger log = LoggerFactory.getLogger(IndexReconciler.class);

    private final TicketRepository ticketRepository;
    private final TicketIngestionRepository ticketIngestionRepository;
    private final IngestionRequestWriter ingestionRequestWriter;
    private final IndexReadiness indexReadiness;
    private final IngestionWorker ingestionWorker;

    public IndexReconciler(
            TicketRepository ticketRepository,
            TicketIngestionRepository ticketIngestionRepository,
            IngestionRequestWriter ingestionRequestWriter,
            IndexReadiness indexReadiness,
            IngestionWorker ingestionWorker) {
        this.ticketRepository = ticketRepository;
        this.ticketIngestionRepository = ticketIngestionRepository;
        this.ingestionRequestWriter = ingestionRequestWriter;
        this.indexReadiness = indexReadiness;
        this.ingestionWorker = ingestionWorker;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reconcile() {
        Instant now = Instant.now();
        List<Ticket> tickets = ticketRepository.findAll();
        Set<String> pending = new HashSet<>();
        for (Ticket ticket : tickets) {
            TicketIngestion row = ticketIngestionRepository.findByTicketId(ticket.getId()).orElse(null);
            if (row == null) {
                ingestionRequestWriter.requestIngestion(ticket);
                row = ticketIngestionRepository.findByTicketId(ticket.getId()).orElseThrow();
            }
            row.setRequestedVersion(ticket.getVersion());
            row.setIndexedVersion(null);
            row.setStatus(com.ticketmanagement.domain.IngestionStatus.PENDING);
            row.setAttemptCount(0);
            row.setRequestedAt(now);
            row.setNextAttemptAt(now);
            row.setLastError(null);
            row.setIndexedAt(null);
            ticketIngestionRepository.save(row);
            pending.add(ticket.getPublicId());
        }
        indexReadiness.resetPending(pending);
        log.info("RAG reconciliation queued {} tickets for ingestion", pending.size());
        ingestionWorker.wake();
    }
}