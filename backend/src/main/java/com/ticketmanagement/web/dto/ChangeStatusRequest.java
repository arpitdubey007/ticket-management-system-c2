package com.ticketmanagement.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChangeStatusRequest(String status, String resolutionNotes) {
}