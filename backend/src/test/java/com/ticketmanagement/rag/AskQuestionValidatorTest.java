package com.ticketmanagement.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ticketmanagement.config.RagProperties;
import com.ticketmanagement.service.RequestValidationException;

class AskQuestionValidatorTest {

    private final AskQuestionValidator validator = new AskQuestionValidator(new RagProperties());

    @Test
    void rejectsBlankQuestion() {
        assertThatThrownBy(() -> validator.validateAndTrim("   "))
                .isInstanceOf(RequestValidationException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void rejectsOverLimitQuestion() {
        String question = "x".repeat(501);
        assertThatThrownBy(() -> validator.validateAndTrim(question))
                .isInstanceOf(RequestValidationException.class);
    }

    @Test
    void trimsAcceptedQuestion() {
        assertThat(validator.validateAndTrim("  hello  ")).isEqualTo("hello");
    }
}