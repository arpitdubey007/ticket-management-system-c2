package com.ticketmanagement.web;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ticketmanagement.domain.Comment;
import com.ticketmanagement.domain.Ticket;
import com.ticketmanagement.domain.TicketStateMachine;
import com.ticketmanagement.domain.TicketStatus;
import com.ticketmanagement.web.dto.CommentResponse;
import com.ticketmanagement.web.dto.TicketResponse;
import com.ticketmanagement.web.dto.TicketSummaryResponse;

@Component
public class TicketMapper {

    public TicketResponse toDetail(Ticket ticket) {
        List<String> allowed = TicketStateMachine.allowedNextStatuses(ticket.getStatus()).stream()
                .map(TicketStatus::name)
                .sorted()
                .toList();
        List<CommentResponse> comments = ticket.getComments().stream()
                .map(this::toComment)
                .toList();
        return new TicketResponse(
                ticket.getPublicId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getCategory(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getAssignee(),
                ticket.getResolutionNotes(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                allowed,
                comments);
    }

    public TicketSummaryResponse toSummary(Ticket ticket) {
        return new TicketSummaryResponse(
                ticket.getPublicId(),
                ticket.getTitle(),
                ticket.getCategory(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getAssignee(),
                ticket.getUpdatedAt());
    }

    public CommentResponse toComment(Comment comment) {
        return new CommentResponse(comment.getAuthor(), comment.getContent(), comment.getCreatedAt());
    }
}