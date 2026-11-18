package com.ticketmanagement.rag;

import org.springframework.ai.document.Document;

public record RetrievedChunk(Document document, double score, boolean namedTicketLookup) {
}