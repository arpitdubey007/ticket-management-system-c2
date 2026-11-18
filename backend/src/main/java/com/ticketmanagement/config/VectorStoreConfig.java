package com.ticketmanagement.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
public class VectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);

    private final EmbeddingModel embeddingModel;
    private final RagProperties ragProperties;

    VectorStoreConfig(EmbeddingModel embeddingModel, RagProperties ragProperties) {
        this.embeddingModel = embeddingModel;
        this.ragProperties = ragProperties;
    }

    @Bean
    VectorStore vectorStore() {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @EventListener(ApplicationReadyEvent.class)
    void logEmbeddingModel() {
        log.info(
                "RAG ready with embedding dimensions {}, topK {}, threshold {}",
                embeddingModel.dimensions(),
                ragProperties.getTopK(),
                ragProperties.getSimilarityThreshold());
    }
}