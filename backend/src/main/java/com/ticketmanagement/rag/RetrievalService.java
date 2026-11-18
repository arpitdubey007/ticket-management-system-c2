package com.ticketmanagement.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

import com.ticketmanagement.config.RagProperties;
import com.ticketmanagement.repository.TicketRepository;

@Service
public class RetrievalService {

    private final KnowledgeIndex knowledgeIndex;
    private final RagProperties ragProperties;
    private final TicketIdExtractor ticketIdExtractor;
    private final ChunkVisibilityFilter visibilityFilter;
    private final TicketRepository ticketRepository;

    public RetrievalService(
            KnowledgeIndex knowledgeIndex,
            RagProperties ragProperties,
            TicketIdExtractor ticketIdExtractor,
            ChunkVisibilityFilter visibilityFilter,
            TicketRepository ticketRepository) {
        this.knowledgeIndex = knowledgeIndex;
        this.ragProperties = ragProperties;
        this.ticketIdExtractor = ticketIdExtractor;
        this.visibilityFilter = visibilityFilter;
        this.ticketRepository = ticketRepository;
    }

    public RetrievalResult retrieve(String question) {
        Set<String> namedIds = ticketIdExtractor.extract(question);
        if (!namedIds.isEmpty()) {
            boolean anyNamedExists =
                    namedIds.stream().anyMatch(id -> ticketRepository.findByPublicId(id).isPresent());
            if (!anyNamedExists) {
                return new RetrievalResult(List.of(), Set.of());
            }
        }

        String query = ragProperties.getEmbedding().getQueryPrefix() + question;
        int topK = ragProperties.getTopK();
        int candidates = topK * ragProperties.getRetrieval().getCandidateMultiplier();
        double threshold = ragProperties.getSimilarityThreshold();

        List<Document> raw = knowledgeIndex.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(candidates)
                .similarityThreshold(0.0)
                .build());

        List<RetrievedChunk> similarityChunks = raw.stream()
                .map(doc -> new RetrievedChunk(doc, score(doc), false))
                .toList();

        List<RetrievedChunk> visibleSimilarity = visibilityFilter.filterVisible(similarityChunks);
        List<RetrievedChunk> aboveThreshold = visibleSimilarity.stream()
                .filter(chunk -> chunk.score() >= threshold)
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(topK)
                .toList();

        Map<String, RetrievedChunk> merged = new LinkedHashMap<>();
        for (RetrievedChunk chunk : aboveThreshold) {
            merged.putIfAbsent(chunk.document().getId(), chunk);
        }

        for (String ticketId : namedIds) {
            if (!ticketRepository.findByPublicId(ticketId).isPresent()) {
                continue;
            }
            List<Document> byTicket = knowledgeIndex.findByTicketId(ticketId);
            List<RetrievedChunk> namedChunks = byTicket.stream()
                    .map(doc -> new RetrievedChunk(doc, score(doc), true))
                    .toList();
            for (RetrievedChunk chunk : visibilityFilter.filterVisible(namedChunks)) {
                merged.putIfAbsent(chunk.document().getId(), chunk);
            }
        }

        List<RetrievedChunk> ordered = merged.values().stream()
                .sorted(Comparator.comparing(RetrievedChunk::namedTicketLookup).reversed()
                        .thenComparing(RetrievedChunk::score, Comparator.reverseOrder()))
                .toList();

        List<RetrievedChunk> trimmed = trimContext(ordered);
        Set<String> ticketIds = trimmed.stream()
                .map(chunk -> chunk.document().getMetadata().get(ChunkMetadataKeys.TICKET_ID))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toSet());

        return new RetrievalResult(trimmed, ticketIds);
    }

    private List<RetrievedChunk> trimContext(List<RetrievedChunk> chunks) {
        int maxChars = ragProperties.getContext().getMaxChars();
        List<RetrievedChunk> kept = new ArrayList<>();
        int used = 0;
        for (RetrievedChunk chunk : chunks) {
            String text = chunk.document().getText();
            int len = text == null ? 0 : text.length();
            if (used + len > maxChars && !kept.isEmpty()) {
                break;
            }
            kept.add(chunk);
            used += len;
        }
        return kept;
    }

    private static double score(Document document) {
        Double value = document.getScore();
        return value == null ? 0.0 : value;
    }
}