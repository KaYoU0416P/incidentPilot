package com.incidentpilot.knowledge.ingestion;

import java.util.Map;

public record ChunkInput(
        int chunkIndex,
        String content,
        Map<String, Object> metadata
) {

    public ChunkInput {
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("metadata must not be null");
        }

        content = content.strip();
        metadata = Map.copyOf(metadata);
    }
}
