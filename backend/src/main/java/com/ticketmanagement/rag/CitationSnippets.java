package com.ticketmanagement.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ticketmanagement.web.dto.CitationResponse;

@Component
public class CitationSnippets {

    private static final int SNIPPET_MAX = 300;

    public List<CitationResponse> build(Set<String> citedTicketIds, List<RetrievedChunk> chunks) {
        Map<String, RetrievedChunk> bestByTicket = new HashMap<>();
        for (RetrievedChunk chunk : chunks) {
            Object ticketIdValue = chunk.document().getMetadata().get(ChunkMetadataKeys.TICKET_ID);
            if (ticketIdValue == null) {
                continue;
            }
            String ticketId = ticketIdValue.toString();
            if (!citedTicketIds.contains(ticketId)) {
                continue;
            }
            bestByTicket.merge(
                    ticketId,
                    chunk,
                    (left, right) -> left.score() >= right.score() ? left : right);
        }

        List<CitationResponse> citations = new ArrayList<>();
        for (String ticketId : citedTicketIds) {
            RetrievedChunk chunk = bestByTicket.get(ticketId);
            if (chunk == null) {
                citations.add(new CitationResponse(ticketId, null));
                continue;
            }
            String text = chunk.document().getText();
            if (text == null || text.isBlank()) {
                citations.add(new CitationResponse(ticketId, null));
                continue;
            }
            citations.add(new CitationResponse(ticketId, formatSnippet(text)));
        }
        citations.sort(Comparator.comparing(CitationResponse::ticketId));
        return citations;
    }

    static String formatSnippet(String text) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        if (oneLine.length() <= SNIPPET_MAX) {
            return oneLine;
        }
        return oneLine.substring(0, SNIPPET_MAX);
    }
}