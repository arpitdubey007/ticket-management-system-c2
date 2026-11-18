package com.ticketmanagement.domain;

import java.util.regex.Pattern;

public final class PublicTicketId {

    private static final Pattern FORMAT = Pattern.compile("^TKT-\\d+$");

    private PublicTicketId() {
    }

    public static boolean isValid(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }
}