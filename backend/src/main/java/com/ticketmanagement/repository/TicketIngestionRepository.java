package com.ticketmanagement.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketmanagement.domain.TicketIngestion;

public interface TicketIngestionRepository extends JpaRepository<TicketIngestion, Long> {

    Optional<TicketIngestion> findByTicketId(Long ticketId);
}