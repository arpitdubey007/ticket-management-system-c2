package com.ticketmanagement.rag;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class TicketDocumentExtractor {

    public List<KnowledgeDocument> extract(TicketSnapshot snapshot) {
        List<KnowledgeDocument> documents = new ArrayList<>();
        String description = TextNormalizer.normalize(snapshot.description());
        if (!description.isEmpty()) {
            documents.add(new KnowledgeDocument(ChunkKind.DESCRIPTION, "description", description, null));
        }
        for (TicketSnapshot.CommentSnapshot comment : snapshot.comments()) {
            String body = TextNormalizer.normalize(comment.content());
            if (!body.isEmpty()) {
                documents.add(new KnowledgeDocument(
                        ChunkKind.COMMENT, String.valueOf(comment.id()), body, comment.author()));
            }
        }
        String resolution = TextNormalizer.normalize(snapshot.resolutionNotes());
        if (!resolution.isEmpty()) {
            documents.add(new KnowledgeDocument(ChunkKind.RESOLUTION_NOTES, "resolution", resolution, null));
        }
        return documents;
    }
}