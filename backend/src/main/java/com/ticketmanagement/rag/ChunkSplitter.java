package com.ticketmanagement.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ticketmanagement.config.RagProperties;

public class ChunkSplitter {

    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");

    private final int maxChars;
    private final int overlapChars;

    public ChunkSplitter(RagProperties properties) {
        this.maxChars = properties.getChunk().getMaxChars();
        this.overlapChars = properties.getChunk().getOverlapChars();
    }

    public List<String> split(String body) {
        if (body.length() <= maxChars) {
            return List.of(body);
        }
        List<String> paragraphs = splitParagraphs(body);
        List<String> packed = packParagraphs(paragraphs);
        if (packed.size() == 1 && packed.get(0).length() > maxChars) {
            return splitBySentencesThenLength(packed.get(0));
        }
        return mergeTinyTail(packed);
    }

    private List<String> splitParagraphs(String body) {
        String[] parts = body.split("\n\n");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        if (result.isEmpty() && !body.isBlank()) {
            result.add(body.trim());
        }
        return result;
    }

    private List<String> packParagraphs(List<String> paragraphs) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph.length() > maxChars) {
                flush(current, chunks);
                chunks.addAll(splitBySentencesThenLength(paragraph));
                continue;
            }
            if (current.isEmpty()) {
                current.append(paragraph);
            } else if (current.length() + 2 + paragraph.length() <= maxChars) {
                current.append("\n\n").append(paragraph);
            } else {
                flush(current, chunks);
                current.append(paragraph);
            }
        }
        flush(current, chunks);
        return chunks;
    }

    private List<String> splitBySentencesThenLength(String text) {
        if (text.length() <= maxChars) {
            return List.of(text);
        }
        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_END.matcher(text);
        int start = 0;
        while (matcher.find()) {
            sentences.add(text.substring(start, matcher.end()).trim());
            start = matcher.end();
        }
        if (start < text.length()) {
            sentences.add(text.substring(start).trim());
        }
        if (sentences.isEmpty()) {
            sentences.add(text);
        }
        List<String> packed = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (sentence.length() > maxChars) {
                flush(current, packed);
                packed.addAll(splitByLength(sentence));
            } else if (current.length() + sentence.length() + 1 <= maxChars) {
                if (!current.isEmpty()) {
                    current.append(' ');
                }
                current.append(sentence);
            } else {
                flush(current, packed);
                current.append(sentence);
            }
        }
        flush(current, packed);
        return packed;
    }

    private List<String> splitByLength(String text) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            parts.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - overlapChars, start + 1);
        }
        return parts;
    }

    private List<String> mergeTinyTail(List<String> chunks) {
        if (chunks.size() < 2) {
            return chunks;
        }
        String tail = chunks.get(chunks.size() - 1);
        if (tail.length() >= 100) {
            return chunks;
        }
        String previous = chunks.get(chunks.size() - 2);
        int maxMerged = (int) (maxChars * 1.2);
        if (previous.length() + 2 + tail.length() <= maxMerged) {
            List<String> merged = new ArrayList<>(chunks.subList(0, chunks.size() - 2));
            merged.add(previous + "\n\n" + tail);
            return merged;
        }
        return chunks;
    }

    private static void flush(StringBuilder current, List<String> chunks) {
        if (!current.isEmpty()) {
            chunks.add(current.toString());
            current.setLength(0);
        }
    }
}