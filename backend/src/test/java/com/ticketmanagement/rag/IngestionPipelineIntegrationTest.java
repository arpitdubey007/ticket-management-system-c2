package com.ticketmanagement.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ticketmanagement.domain.IngestionStatus;
import com.ticketmanagement.repository.TicketIngestionRepository;
import com.ticketmanagement.service.TicketService;

@SpringBootTest
@ActiveProfiles("test")
class IngestionPipelineIntegrationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private KnowledgeIndex knowledgeIndex;

    @Autowired
    private TicketIngestionRepository ticketIngestionRepository;

    @Autowired
    private IngestionWorker ingestionWorker;

    @Test
    void updatingTicketRefreshesVectorDocuments() {
        var ticket = ticketService.createTicket("Alpha title", "Alpha body", "TECHNICAL", null, null);
        awaitIngested(ticket.getId());

        List<Document> before = knowledgeIndex.findByTicketId(ticket.getPublicId());
        assertThat(before).isNotEmpty();
        assertThat(before.get(0).getMetadata().get(ChunkMetadataKeys.TITLE)).isEqualTo("Alpha title");

        ticketService.updateTicket(ticket.getPublicId(), "Beta title", null, null, null, false);
        awaitIngested(ticket.getId());

        List<Document> after = knowledgeIndex.findByTicketId(ticket.getPublicId());
        assertThat(after).isNotEmpty();
        assertThat(after).allMatch(doc -> "Beta title".equals(doc.getMetadata().get(ChunkMetadataKeys.TITLE)));
        long distinctVersions = after.stream()
                .map(Document::getMetadata)
                .map(m -> ((Number) m.get(ChunkMetadataKeys.INGEST_VERSION)).longValue())
                .distinct()
                .count();
        assertThat(distinctVersions).isEqualTo(1);
    }

    private void awaitIngested(long ticketId) {
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            ingestionWorker.processDue();
            var row = ticketIngestionRepository.findByTicketId(ticketId).orElseThrow();
            assertThat(row.getStatus()).isEqualTo(IngestionStatus.INDEXED);
        });
    }
}