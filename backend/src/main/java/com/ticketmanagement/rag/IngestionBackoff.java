package com.ticketmanagement.rag;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.ticketmanagement.config.RagProperties;

@Component
public class IngestionBackoff {

    private final RagProperties properties;

    public IngestionBackoff(RagProperties properties) {
        this.properties = properties;
    }

    public Instant nextAttemptAfter(int attemptCount) {
        RagProperties.Backoff backoff = properties.getIngestion().getBackoff();
        long delayMs = backoff.getInitial().toMillis();
        for (int i = 1; i < attemptCount; i++) {
            delayMs = Math.min(delayMs * backoff.getMultiplier(), backoff.getMax().toMillis());
        }
        return Instant.now().plus(Duration.ofMillis(delayMs));
    }
}