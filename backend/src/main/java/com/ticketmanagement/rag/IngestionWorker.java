package com.ticketmanagement.rag;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ticketmanagement.domain.TicketIngestion;
import com.ticketmanagement.repository.TicketIngestionRepository;

@Component
public class IngestionWorker {

    private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);

    private final TicketIngestionRepository ticketIngestionRepository;
    private final IngestionProcessor ingestionProcessor;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean rerunRequested = new AtomicBoolean();

    public IngestionWorker(TicketIngestionRepository ticketIngestionRepository, IngestionProcessor ingestionProcessor) {
        this.ticketIngestionRepository = ticketIngestionRepository;
        this.ingestionProcessor = ingestionProcessor;
    }

    public void processDue() {
        if (!running.compareAndSet(false, true)) {
            rerunRequested.set(true);
            return;
        }
        try {
            do {
                rerunRequested.set(false);
                List<TicketIngestion> due = ticketIngestionRepository.findDuePending(Instant.now());
                for (TicketIngestion row : due) {
                    ingestionProcessor.process(row.getTicketId());
                }
            } while (rerunRequested.get());
        } finally {
            running.set(false);
            if (rerunRequested.get()) {
                processDue();
            }
        }
    }

    public void wake() {
        log.debug("Ingestion wake-up");
        processDue();
    }
}