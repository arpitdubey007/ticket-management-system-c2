package com.ticketmanagement.rag;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ticketmanagement.domain.Comment;
import com.ticketmanagement.domain.Ticket;
import com.ticketmanagement.repository.TicketRepository;

@Component
public class TicketSnapshotReader {

    private final TicketRepository ticketRepository;

    public TicketSnapshotReader(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Transactional(readOnly = true)
    public Optional<TicketSnapshot> readByPublicId(String publicId) {
        return ticketRepository.findWithCommentsByPublicId(publicId).map(this::toSnapshot);
    }

    @Transactional(readOnly = true)
    public Optional<TicketSnapshot> readByTicketId(long ticketId) {
        return ticketRepository.findById(ticketId).flatMap(ticket -> ticketRepository
                .findWithCommentsByPublicId(ticket.getPublicId())
                .map(this::toSnapshot));
    }

    private TicketSnapshot toSnapshot(Ticket ticket) {
        List<TicketSnapshot.CommentSnapshot> comments = ticket.getComments().stream()
                .map(this::toCommentSnapshot)
                .toList();
        return new TicketSnapshot(
                ticket.getPublicId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getCategory(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getAssignee(),
                ticket.getResolutionNotes(),
                ticket.getVersion(),
                comments);
    }

    private TicketSnapshot.CommentSnapshot toCommentSnapshot(Comment comment) {
        return new TicketSnapshot.CommentSnapshot(comment.getId(), comment.getAuthor(), comment.getContent());
    }
}