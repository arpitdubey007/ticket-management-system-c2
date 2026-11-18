package com.ticketmanagement.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticketmanagement.domain.IngestionStatus;
import com.ticketmanagement.domain.TicketIngestion;

public interface TicketIngestionRepository extends JpaRepository<TicketIngestion, Long> {

    Optional<TicketIngestion> findByTicketId(Long ticketId);

    @Query("""
            SELECT ti FROM TicketIngestion ti
            WHERE ti.status = :status
              AND (ti.nextAttemptAt IS NULL OR ti.nextAttemptAt <= :now)
            ORDER BY ti.requestedAt ASC, ti.ticketId ASC
            """)
    List<TicketIngestion> findDuePending(
            @Param("status") IngestionStatus status,
            @Param("now") Instant now);

    default List<TicketIngestion> findDuePending(Instant now) {
        return findDuePending(IngestionStatus.PENDING, now);
    }

    long countByStatus(IngestionStatus status);

    @Query("""
            SELECT MIN(ti.requestedAt) FROM TicketIngestion ti
            WHERE ti.status = :status
            """)
    Instant findOldestRequestedAtByStatus(@Param("status") IngestionStatus status);

    default Instant findOldestPendingRequestedAt() {
        return findOldestRequestedAtByStatus(IngestionStatus.PENDING);
    }

    @Query("""
            SELECT ti FROM TicketIngestion ti
            WHERE ti.ticket.publicId = :publicId
            """)
    Optional<TicketIngestion> findByTicketPublicId(@Param("publicId") String publicId);
}