package com.ticketmanagement.rag;

import java.util.List;

import com.ticketmanagement.domain.TicketPriority;
import com.ticketmanagement.domain.TicketStatus;

public record TicketSnapshot(
        String publicId,
        String title,
        String description,
        String category,
        TicketStatus status,
        TicketPriority priority,
        String assignee,
        String resolutionNotes,
        long version,
        List<CommentSnapshot> comments) {

    public record CommentSnapshot(long id, String author, String content) {
    }
}