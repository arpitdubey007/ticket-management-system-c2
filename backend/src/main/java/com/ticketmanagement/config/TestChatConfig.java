package com.ticketmanagement.config;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class TestChatConfig {

    @Bean
    @Primary
    ScriptableTestChatModel scriptableTestChatModel() {
        return new ScriptableTestChatModel();
    }

    public static final class ScriptableTestChatModel implements ChatModel {

        private final Queue<String> scriptedResponses = new ConcurrentLinkedQueue<>();
        private final AtomicInteger callCount = new AtomicInteger();

        public void enqueue(String answer) {
            scriptedResponses.add(answer);
        }

        public int getCallCount() {
            return callCount.get();
        }

        public void reset() {
            scriptedResponses.clear();
            callCount.set(0);
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            callCount.incrementAndGet();
            String scripted = scriptedResponses.poll();
            if (scripted == null) {
                scripted = "No detail in context.";
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(scripted))));
        }
    }
}