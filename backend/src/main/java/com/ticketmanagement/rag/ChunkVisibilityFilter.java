package com.ticketmanagement.rag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ticketmanagement.repository.TicketIngestionRepository;
import com.ticketmanagement.repository.TicketRepository;

@Component
public class ChunkVisibilityFilter {

    private final TicketRepository ticketRepository;
    private final TicketIngestionRepository ticketIngestionRepository;

    public ChunkVisibilityFilter(
            TicketRepository ticketRepository, TicketIngestionRepository ticketIngestionRepository) {
        this.ticketRepository = ticketRepository;
        this.ticketIngestionRepository = ticketIngestionRepository;
    }

    public List<RetrievedChunk> filterVisible(List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        Set<String> ticketIds = chunks.stream()
                .map(ChunkVisibilityFilter::ticketId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, Long> visibleVersions = loadVisibleVersions(ticketIds);
        return chunks.stream()
                .filter(chunk -> isVisible(chunk, visibleVersions))
                .toList();
    }

    private Map<String, Long> loadVisibleVersions(Set<String> ticketIds) {
        Map<String, Long> versions = new HashMap<>();
        for (String ticketId : ticketIds) {
            if (!ticketRepository.findByPublicId(ticketId).isPresent()) {
                continue;
            }
            ticketIngestionRepository.findByTicketPublicId(ticketId).ifPresent(row -> {
                if (row.getIndexedVersion() != null) {
                    versions.put(ticketId, row.getIndexedVersion());
                }
            });
        }
        return versions;
    }

    private static boolean isVisible(RetrievedChunk chunk, Map<String, Long> visibleVersions) {
        String ticketId = ticketId(chunk);
        if (ticketId == null) {
            return false;
        }
        Long indexedVersion = visibleVersions.get(ticketId);
        if (indexedVersion == null) {
            return false;
        }
        Object ingestVersion = chunk.document().getMetadata().get(ChunkMetadataKeys.INGEST_VERSION);
        if (ingestVersion instanceof Number number) {
            return indexedVersion.equals(number.longValue());
        }
        return false;
    }

    private static String ticketId(RetrievedChunk chunk) {
        Object value = chunk.document().getMetadata().get(ChunkMetadataKeys.TICKET_ID);
        return value == null ? null : value.toString();
    }
}