package com.ticketmanagement.service;

import java.util.List;

import com.ticketmanagement.web.FieldError;

public class RequestValidationException extends RuntimeException {

    private final List<FieldError> fieldErrors;

    public RequestValidationException(String message, List<FieldError> fieldErrors) {
        super(message);
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }
}