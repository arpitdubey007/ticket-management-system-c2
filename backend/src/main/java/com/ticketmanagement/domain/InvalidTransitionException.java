package com.ticketmanagement.domain;

public class InvalidTransitionException extends RuntimeException {

    private final String fieldMessage;

    public InvalidTransitionException(String message, String fieldMessage) {
        super(message);
        this.fieldMessage = fieldMessage;
    }

    public String getFieldMessage() {
        return fieldMessage;
    }
}