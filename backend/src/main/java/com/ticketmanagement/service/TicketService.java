package com.ticketmanagement.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketmanagement.domain.Ticket;
import com.ticketmanagement.domain.TicketNotFoundException;
import com.ticketmanagement.domain.TicketPriority;
import com.ticketmanagement.domain.TicketStatus;
import com.ticketmanagement.domain.TicketConflictException;
import com.ticketmanagement.repository.PublicIdSequence;
import com.ticketmanagement.repository.TicketListRepository;
import com.ticketmanagement.repository.TicketRepository;
import com.ticketmanagement.web.FieldError;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketListRepository ticketListRepository;
    private final PublicIdSequence publicIdSequence;
    private final TicketValidator ticketValidator;
    private final IngestionRequestWriter ingestionRequestWriter;

    public TicketService(
            TicketRepository ticketRepository,
            TicketListRepository ticketListRepository,
            PublicIdSequence publicIdSequence,
            TicketValidator ticketValidator,
            IngestionRequestWriter ingestionRequestWriter) {
        this.ticketRepository = ticketRepository;
        this.ticketListRepository = ticketListRepository;
        this.publicIdSequence = publicIdSequence;
        this.ticketValidator = ticketValidator;
        this.ingestionRequestWriter = ingestionRequestWriter;
    }

    @Transactional
    public Ticket createTicket(
            String title,
            String description,
            String category,
            String priority,
            String assignee) {
        List<FieldError> errors = new ArrayList<>();
        errors.addAll(ticketValidator.validateCreateTitle(title));
        errors.addAll(ticketValidator.validateCreateDescription(description));
        errors.addAll(ticketValidator.validateCategory(category));
        errors.addAll(ticketValidator.validateOptionalPriority(priority));
        errors.addAll(ticketValidator.validateOptionalAssignee(assignee));
        ticketValidator.rejectIfAny(errors);

        TicketPriority resolvedPriority = priority == null
                ? TicketPriority.MEDIUM
                : TicketPriority.fromValue(priority.trim());

        Ticket ticket = new Ticket(
                publicIdSequence.nextPublicId(),
                title,
                description,
                category.trim(),
                resolvedPriority);
        if (assignee != null && !assignee.trim().isEmpty()) {
            ticket.setAssignee(assignee);
        }
        ticket = ticketRepository.save(ticket);
        ingestionRequestWriter.requestIngestion(ticket);
        return ticketRepository.findWithCommentsByPublicId(ticket.getPublicId()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Ticket getTicket(String publicId) {
        ticketValidator.validatePublicId(publicId);
        return ticketRepository.findWithCommentsByPublicId(publicId)
                .orElseThrow(() -> new TicketNotFoundException(publicId));
    }

    @Transactional(readOnly = true)
    public Page<Ticket> listTickets(String searchTerm, String statusFilter, int page, int size) {
        List<FieldError> errors = new ArrayList<>();
        errors.addAll(ticketValidator.validateSearchTerm(searchTerm));
        errors.addAll(ticketValidator.validateStatusFilter(statusFilter));
        errors.addAll(ticketValidator.validatePaging(page, size));
        ticketValidator.rejectIfAny(errors);

        String pattern = null;
        if (searchTerm != null && !searchTerm.isBlank()) {
            pattern = TicketValidator.escapeLikeLiteral(searchTerm.trim());
        }
        TicketStatus status = null;
        if (statusFilter != null && !statusFilter.isBlank()) {
            status = TicketStatus.fromValue(statusFilter.trim());
        }
        return ticketListRepository.findTickets(pattern, status, PageRequest.of(page, size));
    }

    @Transactional
    public Ticket updateTicket(
            String publicId,
            String title,
            String description,
            String priority,
            String assignee,
            boolean assigneeProvided) {
        ticketValidator.validatePublicId(publicId);
        Ticket ticket = ticketRepository.findByPublicId(publicId)
                .orElseThrow(() -> new TicketNotFoundException(publicId));
        rejectIfTerminal(ticket, "updated");

        boolean hasChange = title != null || description != null || priority != null || assigneeProvided;
        if (!hasChange) {
            throw new RequestValidationException(
                    "Request validation failed.",
                    List.of(new FieldError("_body", "at least one editable field is required")));
        }

        List<FieldError> errors = new ArrayList<>();
        errors.addAll(ticketValidator.validateOptionalTitle(title));
        errors.addAll(ticketValidator.validateOptionalDescription(description));
        if (priority != null) {
            errors.addAll(ticketValidator.validateOptionalPriority(priority));
        }
        if (assigneeProvided) {
            if (assignee != null && !assignee.isEmpty()) {
                errors.addAll(ticketValidator.validateOptionalAssignee(assignee));
            }
        }
        ticketValidator.rejectIfAny(errors);

        if (title != null) {
            ticket.setTitle(title);
        }
        if (description != null) {
            ticket.setDescription(description);
        }
        if (priority != null) {
            ticket.setPriority(TicketPriority.fromValue(priority.trim()));
        }
        if (assigneeProvided) {
            ticket.setAssignee(assignee);
        }
        ticket = ticketRepository.save(ticket);
        ingestionRequestWriter.requestIngestion(ticket);
        return ticketRepository.findWithCommentsByPublicId(publicId).orElseThrow();
    }

    static void rejectIfTerminal(Ticket ticket, String action) {
        if (ticket.getStatus().isTerminal()) {
            throw new TicketConflictException(
                    "Ticket " + ticket.getPublicId() + " is " + ticket.getStatus() + " and cannot be "
                            + action + ".");
        }
    }
}