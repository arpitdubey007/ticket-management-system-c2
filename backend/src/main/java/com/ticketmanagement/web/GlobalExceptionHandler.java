package com.ticketmanagement.web;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.ticketmanagement.domain.AnswerNotVerifiedException;
import com.ticketmanagement.domain.AssistantUnavailableException;
import com.ticketmanagement.domain.InvalidTransitionException;
import com.ticketmanagement.domain.TicketConflictException;
import com.ticketmanagement.domain.TicketNotFoundException;
import com.ticketmanagement.service.RequestValidationException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<ApiError> handleValidation(
            RequestValidationException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                ex.getMessage(),
                request.getRequestURI(),
                ex.getFieldErrors()));
    }

    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidTransition(
            InvalidTransitionException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(
                HttpStatus.BAD_REQUEST,
                "INVALID_TRANSITION",
                ex.getMessage(),
                request.getRequestURI(),
                List.of(new FieldError("status", ex.getFieldMessage()))));
    }

    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(TicketNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI(),
                null));
    }

    @ExceptionHandler(TicketConflictException.class)
    public ResponseEntity<ApiError> handleConflict(TicketConflictException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(
                HttpStatus.CONFLICT,
                "CONFLICT",
                ex.getMessage(),
                request.getRequestURI(),
                null));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(
                HttpStatus.CONFLICT,
                "CONFLICT",
                "Ticket was updated by someone else. Reload it and try again.",
                request.getRequestURI(),
                null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBeanValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.toList());
        return ResponseEntity.badRequest().body(error(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed.",
                request.getRequestURI(),
                fieldErrors));
    }

    @ExceptionHandler(AssistantUnavailableException.class)
    public ResponseEntity<ApiError> handleAssistantUnavailable(
            AssistantUnavailableException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SERVICE_UNAVAILABLE",
                ex.getMessage(),
                request.getRequestURI(),
                null));
    }

    @ExceptionHandler(AnswerNotVerifiedException.class)
    public ResponseEntity<ApiError> handleAnswerNotVerified(
            AnswerNotVerifiedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error(
                HttpStatus.BAD_GATEWAY,
                "ANSWER_NOT_VERIFIED",
                ex.getMessage(),
                request.getRequestURI(),
                null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Malformed JSON request.",
                request.getRequestURI(),
                null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {}", request.getRequestURI(), ex);
        return ResponseEntity.internalServerError().body(error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred.",
                request.getRequestURI(),
                null));
    }

    private static ApiError error(
            HttpStatus status,
            String code,
            String message,
            String path,
            List<FieldError> fieldErrors) {
        ApiError body = new ApiError(
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                path,
                Instant.now(),
                fieldErrors);
        return fieldErrors == null || fieldErrors.isEmpty() ? body.withoutFieldErrors() : body;
    }
}