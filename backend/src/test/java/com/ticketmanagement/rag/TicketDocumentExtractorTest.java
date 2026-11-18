package com.ticketmanagement.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ticketmanagement.domain.TicketPriority;
import com.ticketmanagement.domain.TicketStatus;

class TicketDocumentExtractorTest {

    private final TicketDocumentExtractor extractor = new TicketDocumentExtractor();

    @Test
    void extractsDescriptionCommentsAndResolution() {
        TicketSnapshot snapshot = new TicketSnapshot(
                "TKT-1001",
                "Payment issue",
                "Card declined",
                "PAYMENT",
                TicketStatus.RESOLVED,
                TicketPriority.HIGH,
                null,
                "Restarted gateway",
                2L,
                List.of(new TicketSnapshot.CommentSnapshot(5L, "Ada", "Reproduced")));
        List<KnowledgeDocument> docs = extractor.extract(snapshot);
        assertThat(docs).hasSize(3);
        assertThat(docs.stream().map(KnowledgeDocument::kind).toList())
                .containsExactly(ChunkKind.DESCRIPTION, ChunkKind.COMMENT, ChunkKind.RESOLUTION_NOTES);
    }
}