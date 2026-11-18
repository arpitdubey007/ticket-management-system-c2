package com.ticketmanagement.domain;

public class AnswerNotVerifiedException extends RuntimeException {

    public AnswerNotVerifiedException(String message) {
        super(message);
    }
}