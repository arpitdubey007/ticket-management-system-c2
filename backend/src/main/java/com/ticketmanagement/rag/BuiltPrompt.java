package com.ticketmanagement.rag;

import java.util.List;

import org.springframework.ai.chat.messages.Message;

public record BuiltPrompt(List<Message> messages) {
}