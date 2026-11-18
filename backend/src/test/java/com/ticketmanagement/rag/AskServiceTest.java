package com.ticketmanagement.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

import com.ticketmanagement.config.RagProperties;

@ExtendWith(MockitoExtension.class)
class AskServiceTest {

  @Mock
  private AskQuestionValidator askQuestionValidator;

  @Mock
  private IndexReadiness indexReadiness;

  @Mock
  private RetrievalService retrievalService;

  @Mock
  private PromptBuilder promptBuilder;

  @Mock
  private ChatModel chatModel;

  @Mock
  private CitationValidator citationValidator;

  @Mock
  private CitationSnippets citationSnippets;

  @Mock
  private RagProperties ragProperties;

  @Mock
  private AskMetrics askMetrics;

  @InjectMocks
  private AskService askService;

  @Test
  void returnsNoMatchWhenRetrievalIsEmptyWithoutCallingChat() {
    when(askQuestionValidator.validateAndTrim("weather")).thenReturn("weather");
    when(indexReadiness.isReady()).thenReturn(true);
    when(retrievalService.retrieve("weather")).thenReturn(new RetrievalResult(List.of(), Set.of()));

    AskResult result = askService.ask("weather");

    assertThat(result.answer()).isEqualTo(AskResult.NO_MATCH_ANSWER);
    assertThat(result.grounded()).isFalse();
    verify(chatModel, never()).call(any(Prompt.class));
  }

  @Test
  void failClosedWhenCitationValidationFails() {
    when(askQuestionValidator.validateAndTrim("q")).thenReturn("q");
    when(indexReadiness.isReady()).thenReturn(true);
    Document doc =
        new Document("1", "body", java.util.Map.of(ChunkMetadataKeys.TICKET_ID, "TKT-1001"));
  var chunk = new RetrievedChunk(doc, 0.9, false);
  when(retrievalService.retrieve("q")).thenReturn(new RetrievalResult(List.of(chunk), Set.of("TKT-1001")));
  when(promptBuilder.build(any(), any())).thenReturn(new BuiltPrompt(List.of()));
  var ask = new RagProperties.Ask();
  ask.setTimeout(Duration.ofSeconds(5));
  when(ragProperties.getAsk()).thenReturn(ask);
  when(chatModel.call(any(Prompt.class))).thenReturn(
      new org.springframework.ai.chat.model.ChatResponse(
          List.of(
              new org.springframework.ai.chat.model.Generation(
                  new org.springframework.ai.chat.messages.AssistantMessage(
                      "bad [ticket:TKT-9999]")))));
  when(citationValidator.validate(any(), any()))
    .thenReturn(new CitationValidationResult(false, Set.of(), Set.of("TKT-9999")));

  AskResult result = askService.ask("q");

  assertThat(result.answer()).isEqualTo(AskResult.NO_MATCH_ANSWER);
  assertThat(result.outcome()).isEqualTo(AskOutcome.CITATION_REJECTED);
  verify(citationSnippets, never()).build(any(), any());
  }
}