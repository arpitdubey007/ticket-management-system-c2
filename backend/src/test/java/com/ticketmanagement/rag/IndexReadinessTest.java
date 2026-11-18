package com.ticketmanagement.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class IndexReadinessTest {

    @Test
    void notReadyWhilePendingOrFailed() {
        var readiness = new IndexReadiness();
        readiness.resetPending(Set.of("TKT-1001", "TKT-1002"));
        assertThat(readiness.isReady()).isFalse();

        readiness.markIndexed("TKT-1001");
        assertThat(readiness.isReady()).isFalse();

        readiness.markFailed("TKT-1002");
        assertThat(readiness.isReady()).isFalse();
    }

    @Test
    void readyWhenPendingAndFailedCleared() {
        var readiness = new IndexReadiness();
        readiness.resetPending(Set.of("TKT-1001"));
        readiness.markIndexed("TKT-1001");
        assertThat(readiness.isReady()).isTrue();
    }
}