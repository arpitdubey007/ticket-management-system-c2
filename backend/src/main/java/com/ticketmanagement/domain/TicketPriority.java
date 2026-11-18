package com.ticketmanagement.domain;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum TicketPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static TicketPriority fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException(allowedMessage());
        }
        try {
            return TicketPriority.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(allowedMessage());
        }
    }

    private static String allowedMessage() {
        String allowed = Arrays.stream(values())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        return "Unknown ticket priority. Allowed values: " + allowed;
    }
}