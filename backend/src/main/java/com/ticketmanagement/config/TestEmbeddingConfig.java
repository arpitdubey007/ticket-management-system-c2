package com.ticketmanagement.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class TestEmbeddingConfig {

    private static final int DIMENSIONS = 768;

    /**
     * Test-only stand-in so {@code mvn verify} runs without Ollama.
     * Local and default profiles use {@link org.springframework.ai.ollama.OllamaEmbeddingModel}.
     */
    @Bean
    @Primary
    EmbeddingModel testEmbeddingModel() {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                throw new UnsupportedOperationException("Use embed methods");
            }

            @Override
            public float[] embed(Document document) {
                return embed(document.getText());
            }

            @Override
            public float[] embed(String text) {
                float[] vector = new float[DIMENSIONS];
                if (text == null || text.isEmpty()) {
                    vector[0] = 0.001f;
                    return vector;
                }
                for (int i = 0; i < text.length(); i++) {
                    vector[i % DIMENSIONS] += text.charAt(i);
                }
                double norm = 0;
                for (float v : vector) {
                    norm += v * v;
                }
                norm = Math.sqrt(norm);
                if (norm > 0) {
                    for (int i = 0; i < vector.length; i++) {
                        vector[i] = (float) (vector[i] / norm);
                    }
                }
                return vector;
            }

            @Override
            public int dimensions() {
                return DIMENSIONS;
            }
        };
    }
}