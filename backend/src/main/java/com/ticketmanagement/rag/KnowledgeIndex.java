package com.ticketmanagement.rag;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;

public interface KnowledgeIndex {

    void addChunks(List<TicketChunk> chunks);

    void deleteChunkIds(List<String> ids);

    void deleteTicketVersionNot(String ticketId, long ingestVersion);

    List<Document> findByTicketId(String ticketId);

    List<Document> similaritySearch(SearchRequest request);
}