package com.ticketmanagement.web.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AskResponse(
        @JsonProperty("answer") String answer,
        @JsonProperty("grounded") boolean grounded,
        @JsonProperty("citations") List<CitationResponse> citations) {
}