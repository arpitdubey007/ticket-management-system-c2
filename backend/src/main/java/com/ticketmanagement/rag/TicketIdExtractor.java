package com.ticketmanagement.rag;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class TicketIdExtractor {

    private static final Pattern TICKET_ID = Pattern.compile("TKT-\\d+", Pattern.CASE_INSENSITIVE);

    public Set<String> extract(String question) {
        if (question == null || question.isBlank()) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        Matcher matcher = TICKET_ID.matcher(question);
        while (matcher.find()) {
            ids.add(matcher.group().toUpperCase(Locale.ROOT));
        }
        return ids;
    }
}