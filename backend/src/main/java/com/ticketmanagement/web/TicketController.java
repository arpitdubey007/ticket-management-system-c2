package com.ticketmanagement.web;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.ticketmanagement.domain.Ticket;
import com.ticketmanagement.service.TicketService;
import com.ticketmanagement.service.TicketStatusService;
import com.ticketmanagement.web.dto.ChangeStatusRequest;
import com.ticketmanagement.web.dto.CreateTicketRequest;
import com.ticketmanagement.web.dto.PageResponse;
import com.ticketmanagement.web.dto.TicketResponse;
import com.ticketmanagement.web.dto.TicketSummaryResponse;
import com.ticketmanagement.web.dto.UpdateTicketRequest;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final TicketService ticketService;
    private final TicketStatusService ticketStatusService;
    private final TicketMapper ticketMapper;

    public TicketController(
            TicketService ticketService,
            TicketStatusService ticketStatusService,
            TicketMapper ticketMapper) {
        this.ticketService = ticketService;
        this.ticketStatusService = ticketStatusService;
        this.ticketMapper = ticketMapper;
    }

    @GetMapping
    public PageResponse<TicketSummaryResponse> listTickets(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Ticket> tickets = ticketService.listTickets(q, status, page, size);
        return new PageResponse<>(
                tickets.getContent().stream().map(ticketMapper::toSummary).toList(),
                tickets.getNumber(),
                tickets.getSize(),
                tickets.getTotalElements(),
                tickets.getTotalPages());
    }

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(@RequestBody CreateTicketRequest request) {
        Ticket created = ticketService.createTicket(
                request.title(),
                request.description(),
                request.category(),
                request.priority(),
                request.assignee());
        var location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/tickets/{id}")
                .buildAndExpand(created.getPublicId())
                .toUri();
        return ResponseEntity.created(location).body(ticketMapper.toDetail(created));
    }

    @GetMapping("/{publicId}")
    public TicketResponse getTicket(@PathVariable String publicId) {
        return ticketMapper.toDetail(ticketService.getTicket(publicId));
    }

    @PutMapping("/{publicId}")
    public TicketResponse updateTicket(
            @PathVariable String publicId,
            @RequestBody UpdateTicketRequest request) {
        Ticket updated = ticketService.updateTicket(
                publicId,
                request == null ? null : request.getTitle(),
                request == null ? null : request.getDescription(),
                request == null ? null : request.getPriority(),
                request == null ? null : request.getAssignee(),
                request != null && request.isAssigneePresent());
        return ticketMapper.toDetail(updated);
    }

    @PostMapping("/{publicId}/status")
    public TicketResponse changeStatus(
            @PathVariable String publicId,
            @RequestBody ChangeStatusRequest request) {
        Ticket updated = ticketStatusService.changeStatus(
                publicId,
                request == null ? null : request.status(),
                request == null ? null : request.resolutionNotes());
        return ticketMapper.toDetail(updated);
    }

}