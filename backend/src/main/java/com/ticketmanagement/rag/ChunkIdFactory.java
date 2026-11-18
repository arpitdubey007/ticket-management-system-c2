package com.ticketmanagement.rag;

public final class ChunkIdFactory {

    private ChunkIdFactory() {
    }

    public static String create(
            String ticketId,
            long ingestVersion,
            ChunkKind kind,
            String sourceRef,
            int chunkIndex) {
        return ticketId + ":" + ingestVersion + ":" + kind.name() + ":" + sourceRef + ":" + chunkIndex;
    }
}