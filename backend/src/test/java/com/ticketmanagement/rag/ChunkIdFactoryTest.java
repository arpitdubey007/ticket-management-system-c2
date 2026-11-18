package com.ticketmanagement.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChunkIdFactoryTest {

    @Test
    void idsAreDeterministic() {
        String id1 = ChunkIdFactory.create("TKT-1001", 3L, ChunkKind.DESCRIPTION, "description", 0);
        String id2 = ChunkIdFactory.create("TKT-1001", 3L, ChunkKind.DESCRIPTION, "description", 0);
        assertThat(id1).isEqualTo("TKT-1001:3:DESCRIPTION:description:0");
        assertThat(id1).isEqualTo(id2);
    }
}