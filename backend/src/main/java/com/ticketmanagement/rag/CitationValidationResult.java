package com.ticketmanagement.rag;

import java.util.Set;

public record CitationValidationResult(boolean valid, Set<String> citedTicketIds, Set<String> rejectedTicketIds) {
}