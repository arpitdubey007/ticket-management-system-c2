package com.ticketmanagement.rag;

import java.util.Map;

public record TicketChunk(
        String id,
        String text,
        String body,
        Map<String, Object> metadata) {
}