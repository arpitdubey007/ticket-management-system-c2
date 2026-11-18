package com.ticketmanagement.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class CitationSnippetsTest {

  private final CitationSnippets citationSnippets = new CitationSnippets();

  @Test
  void buildsSnippetFromHighestScoringChunk() {
    Document low = new Document(
        "a",
        "low score text",
        Map.of(
            ChunkMetadataKeys.TICKET_ID, "TKT-1001",
            ChunkMetadataKeys.INGEST_VERSION, 1L));
    Document high = new Document(
        "b",
        "Customer sees code 402 on retry.",
        Map.of(
            ChunkMetadataKeys.TICKET_ID, "TKT-1001",
            ChunkMetadataKeys.INGEST_VERSION, 1L));
    List<RetrievedChunk> chunks = List.of(
        new RetrievedChunk(low, 0.2, false),
        new RetrievedChunk(high, 0.9, false));

    var citations = citationSnippets.build(Set.of("TKT-1001"), chunks);

    assertThat(citations).hasSize(1);
    assertThat(citations.get(0).snippet()).isEqualTo("Customer sees code 402 on retry.");
  }

  @Test
  void truncatesSnippetAt300Characters() {
    String longBody = "x".repeat(400);
    Document doc = new Document(
        "c",
        longBody,
        Map.of(ChunkMetadataKeys.TICKET_ID, "TKT-1002", ChunkMetadataKeys.INGEST_VERSION, 1L));
    var citations =
        citationSnippets.build(Set.of("TKT-1002"), List.of(new RetrievedChunk(doc, 1.0, false)));
    assertThat(citations.get(0).snippet()).hasSize(300);
  }
}