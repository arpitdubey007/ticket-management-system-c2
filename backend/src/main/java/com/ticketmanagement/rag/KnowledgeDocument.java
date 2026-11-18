package com.ticketmanagement.rag;

public record KnowledgeDocument(ChunkKind kind, String sourceRef, String body, String commentAuthor) {
}