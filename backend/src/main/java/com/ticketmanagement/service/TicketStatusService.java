package com.ticketmanagement.service;

import java.util.List;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketmanagement.domain.InvalidTransitionException;
import com.ticketmanagement.domain.Ticket;
import com.ticketmanagement.domain.TicketNotFoundException;
import com.ticketmanagement.domain.TicketStateMachine;
import com.ticketmanagement.domain.TicketStatus;
import com.ticketmanagement.domain.TicketConflictException;
import com.ticketmanagement.repository.TicketRepository;
import com.ticketmanagement.web.FieldError;

@Service
public class TicketStatusService {

    private final TicketRepository ticketRepository;
    private final TicketValidator ticketValidator;
    private final IngestionRequestWriter ingestionRequestWriter;

    public TicketStatusService(
            TicketRepository ticketRepository,
            TicketValidator ticketValidator,
            IngestionRequestWriter ingestionRequestWriter) {
        this.ticketRepository = ticketRepository;
        this.ticketValidator = ticketValidator;
        this.ingestionRequestWriter = ingestionRequestWriter;
    }

    @Transactional
    public Ticket changeStatus(String publicId, String status, String resolutionNotes) {
        ticketValidator.validatePublicId(publicId);
        List<FieldError> statusErrors = ticketValidator.validateChangeStatus(status);
        ticketValidator.rejectIfAny(statusErrors);

        Ticket ticket = ticketRepository.findByPublicId(publicId)
                .orElseThrow(() -> new TicketNotFoundException(publicId));

        TicketStatus target = TicketStatus.fromValue(status.trim());
        TicketStatus current = ticket.getStatus();

        try {
            TicketStateMachine.validateTransition(current, target);
        } catch (InvalidTransitionException ex) {
            throw ex;
        }

        if (target == TicketStatus.RESOLVED) {
            List<FieldError> noteErrors = ticketValidator.validateResolutionNotesForResolve(resolutionNotes);
            ticketValidator.rejectIfAny("Resolution notes are required to resolve a ticket.", noteErrors);
            ticket.setResolutionNotes(resolutionNotes);
        }

        ticket.setStatus(target);
        try {
            ticket = ticketRepository.save(ticket);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw conflict(publicId);
        }
        ingestionRequestWriter.requestIngestion(ticket);
        return ticketRepository.findWithCommentsByPublicId(publicId).orElseThrow();
    }

    private static TicketConflictException conflict(String publicId) {
        return new TicketConflictException(
                "Ticket " + publicId + " was updated by someone else. Reload it and try again.");
    }
}