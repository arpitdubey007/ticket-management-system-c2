package com.ticketmanagement.rag;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

@Component
public class SimpleKnowledgeIndex implements KnowledgeIndex {

    private final VectorStore vectorStore;

    public SimpleKnowledgeIndex(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void addChunks(List<TicketChunk> chunks) {
        List<Document> documents = chunks.stream()
                .map(chunk -> new Document(chunk.id(), chunk.text(), chunk.metadata()))
                .toList();
        vectorStore.add(documents);
    }

    @Override
    public void deleteChunkIds(List<String> ids) {
        if (!ids.isEmpty()) {
            vectorStore.delete(ids);
        }
    }

    @Override
    public void deleteTicketVersionNot(String ticketId, long ingestVersion) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        var ticketFilter = builder.eq(ChunkMetadataKeys.TICKET_ID, ticketId);
        var versionFilter = builder.ne(ChunkMetadataKeys.INGEST_VERSION, ingestVersion);
        vectorStore.delete(builder.and(ticketFilter, versionFilter).build());
    }

    @Override
    public List<Document> findByTicketId(String ticketId) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(ticketId)
                .topK(10_000)
                .similarityThreshold(0.0)
                .filterExpression(builder.eq(ChunkMetadataKeys.TICKET_ID, ticketId).build())
                .build());
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        return vectorStore.similaritySearch(request);
    }
}