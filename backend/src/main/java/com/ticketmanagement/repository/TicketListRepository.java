package com.ticketmanagement.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticketmanagement.domain.Ticket;
import com.ticketmanagement.domain.TicketStatus;

public interface TicketListRepository extends JpaRepository<Ticket, Long> {

    @Query("""
            SELECT DISTINCT t FROM Ticket t
            LEFT JOIN t.comments c
            WHERE (:status IS NULL OR t.status = :status)
              AND (
                :pattern IS NULL
                OR LOWER(t.title) LIKE :pattern ESCAPE '\\'
                OR LOWER(t.description) LIKE :pattern ESCAPE '\\'
                OR LOWER(COALESCE(t.resolutionNotes, '')) LIKE :pattern ESCAPE '\\'
                OR LOWER(c.content) LIKE :pattern ESCAPE '\\'
              )
            ORDER BY t.updatedAt DESC, t.id DESC
            """)
    Page<Ticket> findTickets(
            @Param("pattern") String pattern,
            @Param("status") TicketStatus status,
            Pageable pageable);
}