package com.ticketmanagement.rag;

import java.util.List;
import java.util.Set;

public record RetrievalResult(List<RetrievedChunk> chunks, Set<String> retrievedTicketIds) {
}