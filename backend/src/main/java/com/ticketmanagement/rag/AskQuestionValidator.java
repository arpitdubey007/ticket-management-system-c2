package com.ticketmanagement.rag;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ticketmanagement.config.RagProperties;
import com.ticketmanagement.service.RequestValidationException;
import com.ticketmanagement.web.FieldError;

@Component
public class AskQuestionValidator {

    private final RagProperties ragProperties;

    public AskQuestionValidator(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public String validateAndTrim(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new RequestValidationException(
                    "Question must not be blank.",
                    List.of(new FieldError("question", "must not be blank")));
        }
        String trimmed = question.trim();
        int max = ragProperties.getAsk().getQuestionMax();
        if (trimmed.length() > max) {
            throw new RequestValidationException(
                    "Question is too long.",
                    List.of(new FieldError("question", "must be at most " + max + " characters")));
        }
        return trimmed;
    }
}