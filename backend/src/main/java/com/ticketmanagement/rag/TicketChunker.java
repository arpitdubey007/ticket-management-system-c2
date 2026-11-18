package com.ticketmanagement.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ticketmanagement.config.RagProperties;

@Component
public class TicketChunker {

    private final ChunkSplitter chunkSplitter;

    public TicketChunker(RagProperties properties) {
        this.chunkSplitter = new ChunkSplitter(properties);
    }

    public List<TicketChunk> chunk(TicketSnapshot snapshot, List<KnowledgeDocument> documents) {
        List<TicketChunk> chunks = new ArrayList<>();
        String assignee = snapshot.assignee() == null ? "UNASSIGNED" : snapshot.assignee();
        for (KnowledgeDocument document : documents) {
            List<String> bodies = chunkSplitter.split(document.body());
            for (int index = 0; index < bodies.size(); index++) {
                String body = bodies.get(index);
                String header = buildHeader(snapshot, document);
                String text = header + "\n\n" + body;
                String id = ChunkIdFactory.create(
                        snapshot.publicId(),
                        snapshot.version(),
                        document.kind(),
                        document.sourceRef(),
                        index);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put(ChunkMetadataKeys.TICKET_ID, snapshot.publicId());
                metadata.put(ChunkMetadataKeys.STATUS, snapshot.status().name());
                metadata.put(ChunkMetadataKeys.PRIORITY, snapshot.priority().name());
                metadata.put(ChunkMetadataKeys.ASSIGNEE, assignee);
                metadata.put(ChunkMetadataKeys.CATEGORY, snapshot.category());
                metadata.put(ChunkMetadataKeys.KIND, document.kind().name());
                metadata.put(ChunkMetadataKeys.SOURCE_REF, document.sourceRef());
                metadata.put(ChunkMetadataKeys.CHUNK_INDEX, index);
                metadata.put(ChunkMetadataKeys.INGEST_VERSION, snapshot.version());
                metadata.put(ChunkMetadataKeys.CONTENT_HASH, ContentHasher.sha256Hex(body));
                metadata.put(ChunkMetadataKeys.TITLE, snapshot.title());
                chunks.add(new TicketChunk(id, text, body, Map.copyOf(metadata)));
            }
        }
        return chunks;
    }

    private static String buildHeader(TicketSnapshot snapshot, KnowledgeDocument document) {
        String type = switch (document.kind()) {
            case DESCRIPTION -> "Description";
            case COMMENT -> "Comment by " + document.commentAuthor();
            case RESOLUTION_NOTES -> "Resolution notes";
        };
        return "Ticket: " + snapshot.title() + "\nCategory: " + snapshot.category() + "\nType: " + type;
    }
}