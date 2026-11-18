package com.ticketmanagement.web.dto;

import java.time.Instant;

public record CommentResponse(String author, String content, Instant createdAt) {
}