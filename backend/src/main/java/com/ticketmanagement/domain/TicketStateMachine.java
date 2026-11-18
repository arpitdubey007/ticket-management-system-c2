package com.ticketmanagement.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class TicketStateMachine {

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = buildTable();

    private TicketStateMachine() {
    }

    public static Set<TicketStatus> allowedNextStatuses(TicketStatus from) {
        return EnumSet.copyOf(ALLOWED.getOrDefault(from, EnumSet.noneOf(TicketStatus.class)));
    }

    public static void validateTransition(TicketStatus from, TicketStatus to) {
        if (from == to) {
            throw invalidTransition(from, to);
        }
        if (!ALLOWED.getOrDefault(from, EnumSet.noneOf(TicketStatus.class)).contains(to)) {
            throw invalidTransition(from, to);
        }
    }

    public static boolean isAllowed(TicketStatus from, TicketStatus to) {
        return from != to && ALLOWED.getOrDefault(from, EnumSet.noneOf(TicketStatus.class)).contains(to);
    }

    private static InvalidTransitionException invalidTransition(TicketStatus from, TicketStatus to) {
        String allowed = formatAllowedList(from);
        String message = "Cannot change status from " + from + " to " + to + ". Allowed next statuses: " + allowed + ".";
        String fieldMessage = from + " cannot transition to " + to + ".";
        return new InvalidTransitionException(message, fieldMessage);
    }

    public static String formatAllowedList(TicketStatus from) {
        Set<TicketStatus> next = allowedNextStatuses(from);
        if (next.isEmpty()) {
            return "none";
        }
        return next.stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
    }

    private static Map<TicketStatus, Set<TicketStatus>> buildTable() {
        Map<TicketStatus, Set<TicketStatus>> table = new EnumMap<>(TicketStatus.class);
        table.put(TicketStatus.OPEN, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED));
        table.put(TicketStatus.IN_PROGRESS, EnumSet.of(TicketStatus.RESOLVED, TicketStatus.CANCELLED));
        table.put(TicketStatus.RESOLVED, EnumSet.of(TicketStatus.CLOSED));
        table.put(TicketStatus.CLOSED, EnumSet.noneOf(TicketStatus.class));
        table.put(TicketStatus.CANCELLED, EnumSet.noneOf(TicketStatus.class));
        return table;
    }
}