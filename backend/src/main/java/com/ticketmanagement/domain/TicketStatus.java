package com.ticketmanagement.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    CANCELLED;

    private static final Set<TicketStatus> TERMINAL = EnumSet.of(CLOSED, CANCELLED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public static TicketStatus fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException(allowedMessage());
        }
        try {
            return TicketStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(allowedMessage());
        }
    }

    private static String allowedMessage() {
        String allowed = Arrays.stream(values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        return "Unknown ticket status. Allowed values: " + allowed;
    }
}