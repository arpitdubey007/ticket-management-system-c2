package com.ticketmanagement.web.dto;

import java.time.Instant;
import java.util.List;

import com.ticketmanagement.domain.TicketPriority;
import com.ticketmanagement.domain.TicketStatus;

public record TicketResponse(
        String ticketId,
        String title,
        String description,
        String category,
        TicketPriority priority,
        TicketStatus status,
        String assignee,
        String resolutionNotes,
        Instant createdAt,
        Instant updatedAt,
        List<String> allowedTransitions,
        List<CommentResponse> comments) {
}