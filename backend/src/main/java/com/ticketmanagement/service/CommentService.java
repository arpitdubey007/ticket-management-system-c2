package com.ticketmanagement.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketmanagement.config.TicketAppProperties;
import com.ticketmanagement.domain.Comment;
import com.ticketmanagement.domain.Ticket;
import com.ticketmanagement.domain.TicketNotFoundException;
import com.ticketmanagement.domain.TicketConflictException;
import com.ticketmanagement.repository.CommentRepository;
import com.ticketmanagement.repository.TicketRepository;
import com.ticketmanagement.web.FieldError;

import jakarta.persistence.LockModeType;
import jakarta.persistence.EntityManager;

@Service
public class CommentService {

    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;
    private final TicketValidator ticketValidator;
    private final IngestionRequestWriter ingestionRequestWriter;
    private final TicketAppProperties properties;
    private final EntityManager entityManager;

    public CommentService(
            TicketRepository ticketRepository,
            CommentRepository commentRepository,
            TicketValidator ticketValidator,
            IngestionRequestWriter ingestionRequestWriter,
            TicketAppProperties properties,
            EntityManager entityManager) {
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.ticketValidator = ticketValidator;
        this.ingestionRequestWriter = ingestionRequestWriter;
        this.properties = properties;
        this.entityManager = entityManager;
    }

    @Transactional
    public Comment addComment(String publicId, String author, String content) {
        ticketValidator.validatePublicId(publicId);
        List<FieldError> errors = new ArrayList<>();
        errors.addAll(ticketValidator.validateCommentAuthor(author));
        errors.addAll(ticketValidator.validateCommentContent(content));
        ticketValidator.rejectIfAny(errors);

        Ticket ticket = ticketRepository.findByPublicId(publicId)
                .orElseThrow(() -> new TicketNotFoundException(publicId));
        TicketService.rejectIfTerminal(ticket, "commented on");

        long commentCount = commentRepository.countByTicketId(ticket.getId());
        if (commentCount >= properties.getCommentsMaxPerTicket()) {
            throw new RequestValidationException(
                    "Request validation failed.",
                    List.of(new FieldError("content", "comment limit reached for this ticket")));
        }

        Comment comment = new Comment(author, content);
        comment.setTicket(ticket);
        comment = commentRepository.save(comment);

        ticket.touchActivity();
        entityManager.lock(ticket, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        entityManager.flush();
        Ticket refreshed = ticketRepository.findById(ticket.getId()).orElseThrow();
        try {
            ingestionRequestWriter.requestIngestion(refreshed);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new TicketConflictException(
                    "Ticket " + publicId + " was updated by someone else. Reload it and try again.");
        }
        return comment;
    }
}