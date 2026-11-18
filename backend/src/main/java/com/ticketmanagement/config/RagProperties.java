package com.ticketmanagement.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    @Min(1)
    @Max(50)
    private int topK = 5;

    @DecimalMin("0")
    @DecimalMax("1")
    private double similarityThreshold = 0.5;

    @NotNull
    private Chunk chunk = new Chunk();

    @NotNull
    private Embedding embedding = new Embedding();

    @NotNull
    private Retrieval retrieval = new Retrieval();

    @NotNull
    private Ingestion ingestion = new Ingestion();

    @NotNull
    private Context context = new Context();

    @NotNull
    private Ask ask = new Ask();

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public Chunk getChunk() {
        return chunk;
    }

    public void setChunk(Chunk chunk) {
        this.chunk = chunk;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(Retrieval retrieval) {
        this.retrieval = retrieval;
    }

    public Ingestion getIngestion() {
        return ingestion;
    }

    public void setIngestion(Ingestion ingestion) {
        this.ingestion = ingestion;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public Ask getAsk() {
        return ask;
    }

    public void setAsk(Ask ask) {
        this.ask = ask;
    }

    @Validated
    public static class Chunk {

        @Min(100)
        private int maxChars = 1500;

        @Min(0)
        private int overlapChars = 150;

        public int getMaxChars() {
            return maxChars;
        }

        public void setMaxChars(int maxChars) {
            this.maxChars = maxChars;
        }

        public int getOverlapChars() {
            return overlapChars;
        }

        public void setOverlapChars(int overlapChars) {
            this.overlapChars = overlapChars;
        }
    }

    public static class Embedding {
        private String documentPrefix = "";
        private String queryPrefix = "";

        public String getDocumentPrefix() {
            return documentPrefix;
        }

        public void setDocumentPrefix(String documentPrefix) {
            this.documentPrefix = documentPrefix;
        }

        public String getQueryPrefix() {
            return queryPrefix;
        }

        public void setQueryPrefix(String queryPrefix) {
            this.queryPrefix = queryPrefix;
        }
    }

    @Validated
    public static class Retrieval {

        @Min(1)
        @Max(10)
        private int candidateMultiplier = 3;

        public int getCandidateMultiplier() {
            return candidateMultiplier;
        }

        public void setCandidateMultiplier(int candidateMultiplier) {
            this.candidateMultiplier = candidateMultiplier;
        }
    }

    @Validated
    public static class Ingestion {

        @NotNull
        private Duration pollInterval = Duration.ofSeconds(2);

        @Min(1)
        private int maxAttempts = 5;

        @NotNull
        private Backoff backoff = new Backoff();

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Backoff getBackoff() {
            return backoff;
        }

        public void setBackoff(Backoff backoff) {
            this.backoff = backoff;
        }
    }

    public static class Backoff {

        @NotNull
        private Duration initial = Duration.ofSeconds(5);

        @Min(1)
        private int multiplier = 2;

        @NotNull
        private Duration max = Duration.ofMinutes(5);

        public Duration getInitial() {
            return initial;
        }

        public void setInitial(Duration initial) {
            this.initial = initial;
        }

        public int getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(int multiplier) {
            this.multiplier = multiplier;
        }

        public Duration getMax() {
            return max;
        }

        public void setMax(Duration max) {
            this.max = max;
        }
    }

    @Validated
    public static class Context {

        @Min(500)
        private int maxChars = 6000;

        public int getMaxChars() {
            return maxChars;
        }

        public void setMaxChars(int maxChars) {
            this.maxChars = maxChars;
        }
    }

    @Validated
    public static class Ask {

        @Min(1)
        @Max(500)
        private int questionMax = 500;

        @NotNull
        private Duration timeout = Duration.ofSeconds(60);

        public int getQuestionMax() {
            return questionMax;
        }

        public void setQuestionMax(int questionMax) {
            this.questionMax = questionMax;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
}