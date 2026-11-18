package com.ticketmanagement.rag;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import com.ticketmanagement.domain.AssistantUnavailableException;

import com.ticketmanagement.web.dto.CitationResponse;

@Service
public class AskService {

    private final AskQuestionValidator askQuestionValidator;
    private final IndexReadiness indexReadiness;
    private final RetrievalService retrievalService;
    private final PromptBuilder promptBuilder;
    private final ChatModel chatModel;
    private final CitationValidator citationValidator;
    private final CitationSnippets citationSnippets;
    private final com.ticketmanagement.config.RagProperties ragProperties;
    private final AskMetrics askMetrics;
    private final ExecutorService askExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public AskService(
            AskQuestionValidator askQuestionValidator,
            IndexReadiness indexReadiness,
            RetrievalService retrievalService,
            PromptBuilder promptBuilder,
            ChatModel chatModel,
            CitationValidator citationValidator,
            CitationSnippets citationSnippets,
            com.ticketmanagement.config.RagProperties ragProperties,
            AskMetrics askMetrics) {
        this.askQuestionValidator = askQuestionValidator;
        this.indexReadiness = indexReadiness;
        this.retrievalService = retrievalService;
        this.promptBuilder = promptBuilder;
        this.chatModel = chatModel;
        this.citationValidator = citationValidator;
        this.citationSnippets = citationSnippets;
        this.ragProperties = ragProperties;
        this.askMetrics = askMetrics;
    }

    public AskResult ask(String question) {
        String trimmed = askQuestionValidator.validateAndTrim(question);
        if (!indexReadiness.isReady()) {
            throw new AssistantUnavailableException("The assistant is unavailable. Try again later.");
        }

        RetrievalResult retrieval = retrievalService.retrieve(trimmed);
        if (retrieval.chunks().isEmpty()) {
            askMetrics.record(AskOutcome.NO_MATCH, 0);
            return AskResult.noMatch(AskOutcome.NO_MATCH);
        }

        BuiltPrompt built = promptBuilder.build(trimmed, retrieval.chunks());
        String modelAnswer;
        try {
            modelAnswer = callModelWithTimeout(built);
        } catch (AssistantUnavailableException ex) {
            askMetrics.record(AskOutcome.ERROR, retrieval.chunks().size());
            throw ex;
        }

        CitationValidationResult validation =
                citationValidator.validate(modelAnswer, retrieval.retrievedTicketIds());
        if (!validation.valid()) {
            askMetrics.recordCitationRejected(validation.rejectedTicketIds());
            askMetrics.record(AskOutcome.CITATION_REJECTED, retrieval.chunks().size());
            return AskResult.noMatch(AskOutcome.CITATION_REJECTED);
        }

        List<CitationResponse> citations = citationSnippets.build(validation.citedTicketIds(), retrieval.chunks());
        askMetrics.record(AskOutcome.ANSWERED, retrieval.chunks().size());
        return new AskResult(modelAnswer.trim(), true, citations, AskOutcome.ANSWERED);
    }

    private String callModelWithTimeout(BuiltPrompt built) {
        Callable<String> task = () -> {
            ChatResponse response = chatModel.call(new Prompt(built.messages()));
            Generation generation = response.getResult();
            if (generation == null || generation.getOutput() == null) {
                throw new AssistantUnavailableException("The assistant is unavailable. Try again later.");
            }
            return generation.getOutput().getText();
        };
        Future<String> future = askExecutor.submit(task);
        try {
            long timeoutMs = ragProperties.getAsk().getTimeout().toMillis();
            String text = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            if (text == null || text.isBlank()) {
                throw new AssistantUnavailableException("The assistant is unavailable. Try again later.");
            }
            return text;
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new AssistantUnavailableException("The assistant is unavailable. Try again later.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssistantUnavailableException("The assistant is unavailable. Try again later.", ex);
        } catch (ExecutionException ex) {
            throw new AssistantUnavailableException("The assistant is unavailable. Try again later.", ex.getCause());
        }
    }
}