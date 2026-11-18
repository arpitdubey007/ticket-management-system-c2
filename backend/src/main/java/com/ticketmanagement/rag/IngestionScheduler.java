package com.ticketmanagement.rag;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IngestionScheduler {

    private final IngestionWorker ingestionWorker;

    public IngestionScheduler(IngestionWorker ingestionWorker) {
        this.ingestionWorker = ingestionWorker;
    }

    @Scheduled(fixedDelayString = "${app.rag.ingestion.poll-interval}")
    public void poll() {
        ingestionWorker.processDue();
    }
}