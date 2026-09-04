package com.incidentpilot.knowledge.ingestion;

import java.util.UUID;

public record DocumentIngestionResult(
        UUID documentId,
        String contentHash,
        int chunkCount,
        DocumentIngestionStatus status
) {
}
