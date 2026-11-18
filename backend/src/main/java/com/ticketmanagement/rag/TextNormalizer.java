package com.ticketmanagement.rag;

public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n').trim();
        return text.replaceAll("\n{3,}", "\n\n");
    }
}