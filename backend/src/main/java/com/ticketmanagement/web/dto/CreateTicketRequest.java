package com.ticketmanagement.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateTicketRequest(
        String title,
        String description,
        String category,
        String priority,
        String assignee,
        String ticketId,
        String status,
        String createdAt,
        String updatedAt) {
}