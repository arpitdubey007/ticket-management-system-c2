package com.ticketmanagement.rag;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AskMetrics {

    private static final Logger log = LoggerFactory.getLogger(AskMetrics.class);

    public void record(AskOutcome outcome, int chunkCount) {
        log.info("Ask outcome={} chunksRetrieved={}", outcome, chunkCount);
    }

    public void recordCitationRejected(Set<String> rejectedIds) {
        log.warn("Ask outcome=CITATION_REJECTED rejectedTicketIds={}", rejectedIds);
    }
}