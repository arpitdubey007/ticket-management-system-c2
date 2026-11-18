package com.ticketmanagement.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ticketmanagement.config.RagProperties;
import com.ticketmanagement.domain.TicketPriority;
import com.ticketmanagement.domain.TicketStatus;

class TicketChunkerTest {

    private final TicketChunker chunker = new TicketChunker(new RagProperties());

    @Test
    void chunkContainsRequiredMetadataAndHeader() {
        TicketSnapshot snapshot = new TicketSnapshot(
                "TKT-1001",
                "Payment issue",
                "Card declined",
                "PAYMENT",
                TicketStatus.OPEN,
                TicketPriority.MEDIUM,
                null,
                null,
                1L,
                List.of());
        KnowledgeDocument doc = new KnowledgeDocument(ChunkKind.DESCRIPTION, "description", "Card declined", null);
        List<TicketChunk> chunks = chunker.chunk(snapshot, List.of(doc));
        assertThat(chunks).hasSize(1);
        TicketChunk chunk = chunks.get(0);
        assertThat(chunk.text()).startsWith("Ticket: Payment issue\nCategory: PAYMENT\nType: Description\n\n");
        assertThat(chunk.metadata()).containsEntry(ChunkMetadataKeys.TICKET_ID, "TKT-1001");
        assertThat(chunk.metadata()).containsEntry(ChunkMetadataKeys.ASSIGNEE, "UNASSIGNED");
        assertThat(chunk.metadata()).containsEntry(ChunkMetadataKeys.STATUS, "OPEN");
        assertThat(chunk.metadata()).containsEntry(ChunkMetadataKeys.PRIORITY, "MEDIUM");
        assertThat(chunk.metadata()).containsEntry(ChunkMetadataKeys.CATEGORY, "PAYMENT");
    }
}