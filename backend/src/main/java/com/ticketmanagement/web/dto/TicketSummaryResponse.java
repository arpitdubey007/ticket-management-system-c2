package com.ticketmanagement.web.dto;

import java.time.Instant;

import com.ticketmanagement.domain.TicketPriority;
import com.ticketmanagement.domain.TicketStatus;

public record TicketSummaryResponse(
        String ticketId,
        String title,
        String category,
        TicketPriority priority,
        TicketStatus status,
        String assignee,
        Instant updatedAt) {
}