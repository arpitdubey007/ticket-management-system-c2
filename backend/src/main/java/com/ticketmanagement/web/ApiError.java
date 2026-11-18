package com.ticketmanagement.web;

import java.time.Instant;
import java.util.List;

public record ApiError(
        int status,
        String error,
        String code,
        String message,
        String path,
        Instant timestamp,
        List<FieldError> fieldErrors) {

    public ApiError withoutFieldErrors() {
        return new ApiError(status, error, code, message, path, timestamp, null);
    }
}