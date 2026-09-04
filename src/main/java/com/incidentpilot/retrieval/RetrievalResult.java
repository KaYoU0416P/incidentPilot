package com.incidentpilot.retrieval;

import java.util.List;

public record RetrievalResult(
        String retrieverName,
        List<RetrievedChunk> chunks) {
    public RetrievalResult {
        if (retrieverName == null || retrieverName.isBlank()) {
            throw new IllegalArgumentException("retrieverName must not be blank");
        }

        if (chunks == null) {
            throw new IllegalArgumentException("chunks must not be null");
        }

        retrieverName = retrieverName.strip();
        chunks = List.copyOf(chunks);
    }
}
