package com.ticketmanagement.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ticketmanagement.config.TicketAppProperties;
import com.ticketmanagement.domain.PublicTicketId;
import com.ticketmanagement.domain.TicketPriority;
import com.ticketmanagement.domain.TicketStatus;
import com.ticketmanagement.web.FieldError;

@Component
public class TicketValidator {

    private static final String RESERVED_ASSIGNEE = "UNASSIGNED";

    private final TicketAppProperties properties;

    public TicketValidator(TicketAppProperties properties) {
        this.properties = properties;
    }

    public void validatePublicId(String publicId) {
        if (!PublicTicketId.isValid(publicId)) {
            throw validationFailure(
                    "Invalid ticket id.",
                    List.of(new FieldError("ticketId", "must match TKT-<number>")));
        }
    }

    public List<FieldError> validateCreateTitle(String title) {
        return validateRequiredText("title", title, properties.getLimits().getTitleMax());
    }

    public List<FieldError> validateCreateDescription(String description) {
        return validateRequiredText("description", description, properties.getLimits().getDescriptionMax());
    }

    public List<FieldError> validateCategory(String category) {
        List<FieldError> errors = new ArrayList<>();
        if (category == null || category.trim().isEmpty()) {
            errors.add(new FieldError("category", "must not be blank"));
            return errors;
        }
        String trimmed = category.trim();
        if (!properties.getCategories().contains(trimmed)) {
            errors.add(new FieldError(
                    "category",
                    "must be one of " + String.join(", ", properties.getCategories())));
        }
        return errors;
    }

    public List<FieldError> validateOptionalPriority(String priority) {
        if (priority == null) {
            return List.of();
        }
        try {
            TicketPriority.fromValue(priority.trim());
            return List.of();
        } catch (IllegalArgumentException ex) {
            return List.of(new FieldError("priority", ex.getMessage()));
        }
    }

    public List<FieldError> validateOptionalAssignee(String assignee) {
        if (assignee == null) {
            return List.of();
        }
        if (assignee.isEmpty()) {
            return List.of(new FieldError("assignee", "must not be blank"));
        }
        String trimmed = assignee.trim();
        if (trimmed.isEmpty()) {
            return List.of(new FieldError("assignee", "must not be blank"));
        }
        if (RESERVED_ASSIGNEE.equalsIgnoreCase(trimmed)) {
            return List.of(new FieldError("assignee", "UNASSIGNED is reserved"));
        }
        if (trimmed.length() > properties.getLimits().getAssigneeMax()) {
            return List.of(new FieldError(
                    "assignee",
                    "must be at most " + properties.getLimits().getAssigneeMax() + " characters"));
        }
        return List.of();
    }

    public List<FieldError> validateOptionalTitle(String title) {
        if (title == null) {
            return List.of();
        }
        return validateRequiredText("title", title, properties.getLimits().getTitleMax());
    }

    public List<FieldError> validateOptionalDescription(String description) {
        if (description == null) {
            return List.of();
        }
        return validateRequiredText("description", description, properties.getLimits().getDescriptionMax());
    }

    public List<FieldError> validateStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return List.of();
        }
        try {
            TicketStatus.fromValue(status.trim());
            return List.of();
        } catch (IllegalArgumentException ex) {
            String allowed = Arrays.stream(TicketStatus.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            return List.of(new FieldError("status", "must be one of " + allowed));
        }
    }

    public List<FieldError> validateSearchTerm(String term) {
        if (term == null || term.isBlank()) {
            return List.of();
        }
        String trimmed = term.trim();
        if (trimmed.length() > properties.getLimits().getSearchTermMax()) {
            return List.of(new FieldError(
                    "q",
                    "must be at most " + properties.getLimits().getSearchTermMax() + " characters"));
        }
        return List.of();
    }

    public List<FieldError> validatePaging(int page, int size) {
        List<FieldError> errors = new ArrayList<>();
        if (page < 0) {
            errors.add(new FieldError("page", "must be at least 0"));
        }
        if (size < 1 || size > 100) {
            errors.add(new FieldError("size", "must be between 1 and 100"));
        }
        return errors;
    }

    public List<FieldError> validateChangeStatus(String status) {
        if (status == null || status.isBlank()) {
            return List.of(new FieldError("status", "must not be blank"));
        }
        try {
            TicketStatus.fromValue(status.trim());
            return List.of();
        } catch (IllegalArgumentException ex) {
            String allowed = Arrays.stream(TicketStatus.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            return List.of(new FieldError("status", "must be one of " + allowed));
        }
    }

    public List<FieldError> validateResolutionNotesForResolve(String notes) {
        if (notes == null || notes.trim().isEmpty()) {
            return List.of(new FieldError("resolutionNotes", "must not be blank"));
        }
        String trimmed = notes.trim();
        if (trimmed.length() > properties.getLimits().getResolutionNotesMax()) {
            return List.of(new FieldError(
                    "resolutionNotes",
                    "must be at most " + properties.getLimits().getResolutionNotesMax() + " characters"));
        }
        return List.of();
    }

    public List<FieldError> validateCommentAuthor(String author) {
        return validateRequiredText("author", author, properties.getLimits().getAuthorMax());
    }

    public List<FieldError> validateCommentContent(String content) {
        return validateRequiredText("content", content, properties.getLimits().getCommentContentMax());
    }

    public void rejectIfAny(List<FieldError> errors) {
        if (!errors.isEmpty()) {
            throw validationFailure("Request validation failed.", errors);
        }
    }

    public void rejectIfAny(String message, List<FieldError> errors) {
        if (!errors.isEmpty()) {
            throw validationFailure(message, errors);
        }
    }

    public static String escapeLikeLiteral(String term) {
        String escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped.toLowerCase(Locale.ROOT) + "%";
    }

    private List<FieldError> validateRequiredText(String field, String value, int maxLength) {
        List<FieldError> errors = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) {
            errors.add(new FieldError(field, "must not be blank"));
            return errors;
        }
        if (value.trim().length() > maxLength) {
            errors.add(new FieldError(field, "must be at most " + maxLength + " characters"));
        }
        return errors;
    }

    private static RequestValidationException validationFailure(String message, List<FieldError> errors) {
        return new RequestValidationException(message, errors);
    }
}