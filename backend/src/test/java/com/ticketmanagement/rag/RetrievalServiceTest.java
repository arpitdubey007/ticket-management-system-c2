package com.ticketmanagement.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;

import com.ticketmanagement.config.RagProperties;
import com.ticketmanagement.domain.Ticket;
import com.ticketmanagement.repository.TicketRepository;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {

    @Mock
    private KnowledgeIndex knowledgeIndex;

    @Mock
    private ChunkVisibilityFilter visibilityFilter;

    @Mock
    private TicketRepository ticketRepository;

    private RagProperties ragProperties;
    private RetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        ragProperties.setTopK(2);
        ragProperties.setSimilarityThreshold(0.5);
        retrievalService = new RetrievalService(
                knowledgeIndex,
                ragProperties,
                new TicketIdExtractor(),
                visibilityFilter,
                ticketRepository);
    }

    @Test
    void keepsOnlyChunksAtOrAboveThresholdUpToTopK() {
        Document high = scoredDoc("high", "TKT-1001", 0.9);
        Document low = scoredDoc("low", "TKT-1002", 0.2);
        when(knowledgeIndex.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(high, low));
        when(visibilityFilter.filterVisible(any())).thenAnswer(inv -> inv.getArgument(0));

        RetrievalResult result = retrievalService.retrieve("payment failures");

        assertThat(result.chunks()).hasSize(1);
        assertThat(result.retrievedTicketIds()).containsExactly("TKT-1001");
    }

    @Test
    void returnsEmptyWhenAllScoresBelowThreshold() {
        Document low = scoredDoc("low", "TKT-1001", 0.1);
        when(knowledgeIndex.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(low));
        when(visibilityFilter.filterVisible(any())).thenAnswer(inv -> inv.getArgument(0));

        RetrievalResult result = retrievalService.retrieve("unrelated weather");

        assertThat(result.chunks()).isEmpty();
        verify(knowledgeIndex, never()).findByTicketId(anyString());
    }

    @Test
    void includesNamedTicketEvenWhenScoreIsLow() {
        Document low = scoredDoc("low", "TKT-1001", 0.0);
        when(knowledgeIndex.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(ticketRepository.findByPublicId("TKT-1001"))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(Ticket.class)));
        when(knowledgeIndex.findByTicketId("TKT-1001")).thenReturn(List.of(low));
        when(visibilityFilter.filterVisible(any())).thenAnswer(inv -> inv.getArgument(0));

        RetrievalResult result = retrievalService.retrieve("What happened on TKT-1001?");

        assertThat(result.chunks()).hasSize(1);
        assertThat(result.retrievedTicketIds()).contains("TKT-1001");
    }

  @Test
    void ignoresNonExistentNamedTicket() {
        when(knowledgeIndex.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(ticketRepository.findByPublicId("TKT-9999")).thenReturn(Optional.empty());

        RetrievalResult result = retrievalService.retrieve("Check TKT-9999");

        assertThat(result.chunks()).isEmpty();
        verify(knowledgeIndex, never()).findByTicketId("TKT-9999");
    }

    private static Document scoredDoc(String id, String ticketId, double score) {
        return Document.builder()
                .id(id)
                .text("body")
                .metadata(Map.of(
                        ChunkMetadataKeys.TICKET_ID,
                        ticketId,
                        ChunkMetadataKeys.INGEST_VERSION,
                        1L))
                .score(score)
                .build();
    }
}