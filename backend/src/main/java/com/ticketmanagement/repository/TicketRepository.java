package com.ticketmanagement.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketmanagement.domain.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByPublicId(String publicId);

    @EntityGraph(attributePaths = "comments")
    Optional<Ticket> findWithCommentsByPublicId(String publicId);
}