package com.ticketmanagement.rag;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class IngestionWakeUp {

    private final IngestionWorker ingestionWorker;

    public IngestionWakeUp(IngestionWorker ingestionWorker) {
        this.ingestionWorker = ingestionWorker;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterTicketChange(TicketChangedEvent event) {
        ingestionWorker.wake();
    }
}