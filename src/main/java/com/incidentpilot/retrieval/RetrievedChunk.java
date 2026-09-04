package com.incidentpilot.retrieval;

import java.util.UUID;

public record RetrievedChunk(
        UUID sourceId,
        UUID chunkId,
        String content,
        String sourceLocator,
        double score
) {
    public RetrievedChunk {
        if (sourceId == null) {
            throw new IllegalArgumentException("sourceId must not be null");
        }

        if (chunkId == null) {
            throw new IllegalArgumentException("chunkId must not be null");
        }

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }

        if (sourceLocator == null || sourceLocator.isBlank()) {
            throw new IllegalArgumentException("sourceLocator must not be blank");
        }

        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }

        content = content.strip();
        sourceLocator = sourceLocator.strip();
    }
}
