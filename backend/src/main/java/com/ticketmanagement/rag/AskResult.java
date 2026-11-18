package com.ticketmanagement.rag;

import java.util.List;

import com.ticketmanagement.web.dto.CitationResponse;

public record AskResult(String answer, boolean grounded, List<CitationResponse> citations, AskOutcome outcome) {

    public static final String NO_MATCH_ANSWER = "No relevant tickets found.";

    public static AskResult noMatch(AskOutcome outcome) {
        return new AskResult(NO_MATCH_ANSWER, false, List.of(), outcome);
    }
}