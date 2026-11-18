package com.ticketmanagement.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "ticket_ingestion")
public class TicketIngestion {

    @Id
    @Column(name = "ticket_id")
    private Long ticketId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private IngestionStatus status = IngestionStatus.PENDING;

    @Column(name = "requested_version", nullable = false)
    private Long requestedVersion;

    @Column(name = "indexed_version")
    private Long indexedVersion;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "indexed_at")
    private Instant indexedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    protected TicketIngestion() {
    }

    public TicketIngestion(Ticket ticket, long requestedVersion, Instant now) {
        this.ticket = ticket;
        this.requestedVersion = requestedVersion;
        this.status = IngestionStatus.PENDING;
        this.attemptCount = 0;
        this.requestedAt = now;
        this.nextAttemptAt = now;
    }

    @PrePersist
    void onCreate() {
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = requestedAt;
        }
    }

    public Long getTicketId() {
        return ticketId;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public IngestionStatus getStatus() {
        return status;
    }

    public void setStatus(IngestionStatus status) {
        this.status = status;
    }

    public Long getRequestedVersion() {
        return requestedVersion;
    }

    public void setRequestedVersion(Long requestedVersion) {
        this.requestedVersion = requestedVersion;
    }

    public Long getIndexedVersion() {
        return indexedVersion;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Instant getIndexedAt() {
        return indexedAt;
    }

    public void setIndexedAt(Instant indexedAt) {
        this.indexedAt = indexedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}