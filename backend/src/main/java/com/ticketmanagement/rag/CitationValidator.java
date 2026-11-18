package com.ticketmanagement.rag;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class CitationValidator {

    private static final Pattern CITATION = Pattern.compile("\\[ticket:(TKT-\\d+)]", Pattern.CASE_INSENSITIVE);

    public CitationValidationResult validate(String answer, Set<String> retrievedTicketIds) {
        if (answer == null || answer.isBlank()) {
            return new CitationValidationResult(false, Set.of(), Set.of());
        }
        Set<String> cited = new LinkedHashSet<>();
        Set<String> rejected = new LinkedHashSet<>();
        Matcher matcher = CITATION.matcher(answer);
        while (matcher.find()) {
            String ticketId = matcher.group(1).toUpperCase(Locale.ROOT);
            if (retrievedTicketIds.contains(ticketId)) {
                cited.add(ticketId);
            } else {
                rejected.add(ticketId);
            }
        }
        if (!rejected.isEmpty() || cited.isEmpty()) {
            return new CitationValidationResult(false, cited, rejected);
        }
        return new CitationValidationResult(true, cited, Set.of());
    }
}