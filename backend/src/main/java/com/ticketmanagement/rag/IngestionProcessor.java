package com.ticketmanagement.rag;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ticketmanagement.domain.IngestionStatus;
import com.ticketmanagement.domain.TicketIngestion;
import com.ticketmanagement.repository.TicketIngestionRepository;

@Component
public class IngestionProcessor {

    private static final Logger log = LoggerFactory.getLogger(IngestionProcessor.class);

    private final TicketSnapshotReader snapshotReader;
    private final TicketDocumentExtractor documentExtractor;
    private final TicketChunker ticketChunker;
    private final KnowledgeIndex knowledgeIndex;
    private final TicketIngestionRepository ticketIngestionRepository;
    private final IngestionOutcomeWriter ingestionOutcomeWriter;

    public IngestionProcessor(
            TicketSnapshotReader snapshotReader,
            TicketDocumentExtractor documentExtractor,
            TicketChunker ticketChunker,
            KnowledgeIndex knowledgeIndex,
            TicketIngestionRepository ticketIngestionRepository,
            IngestionOutcomeWriter ingestionOutcomeWriter) {
        this.snapshotReader = snapshotReader;
        this.documentExtractor = documentExtractor;
        this.ticketChunker = ticketChunker;
        this.knowledgeIndex = knowledgeIndex;
        this.ticketIngestionRepository = ticketIngestionRepository;
        this.ingestionOutcomeWriter = ingestionOutcomeWriter;
    }

    public void process(long ticketId) {
        TicketIngestion row = ticketIngestionRepository.findById(ticketId).orElse(null);
        if (row == null || row.getStatus() != IngestionStatus.PENDING) {
            return;
        }
        TicketSnapshot snapshot = snapshotReader.readByTicketId(ticketId).orElse(null);
        if (snapshot == null) {
            ingestionOutcomeWriter.recordFailure(ticketId, "ticket missing");
            return;
        }
        if (!row.getRequestedVersion().equals(snapshot.version())) {
            return;
        }
        List<TicketChunk> chunks = buildChunks(snapshot);
        List<String> addedIds = new ArrayList<>();
        try {
            knowledgeIndex.addChunks(chunks);
            addedIds.addAll(chunks.stream().map(TicketChunk::id).toList());
            ingestionOutcomeWriter.commitSuccess(ticketId, snapshot.version());
            knowledgeIndex.deleteTicketVersionNot(snapshot.publicId(), snapshot.version());
        } catch (RuntimeException ex) {
            log.warn(
                    "Ingestion failed for ticket {} attempt {}: {}",
                    snapshot.publicId(),
                    row.getAttemptCount(),
                    ex.toString());
            knowledgeIndex.deleteChunkIds(addedIds);
            ingestionOutcomeWriter.recordFailure(ticketId, sanitize(ex.getMessage()));
        }
    }

    private List<TicketChunk> buildChunks(TicketSnapshot snapshot) {
        List<KnowledgeDocument> documents = documentExtractor.extract(snapshot);
        return ticketChunker.chunk(snapshot, documents);
    }

    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "ingestion failed";
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}