package com.incidentpilot.knowledge.ingestion;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record DocumentIngestionRequest(
        String sourceKey,
        String title,
        String documentType,
        String serviceName,
        String sourceUri,
        List<ChunkInput> chunks
) {

    public DocumentIngestionRequest {
        sourceKey = requireText(sourceKey, "sourceKey");
        title = requireText(title, "title");
        documentType = requireText(documentType, "documentType");
        sourceUri = requireText(sourceUri, "sourceUri");
        serviceName = normalizeOptionalText(serviceName);

        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException("chunks must not be empty");
        }

        chunks = chunks.stream()
                .sorted(Comparator.comparingInt(ChunkInput::chunkIndex))
                .toList();

        Set<Integer> indexes = new HashSet<>();
        for (ChunkInput chunk : chunks) {
            if (!indexes.add(chunk.chunkIndex())) {
                throw new IllegalArgumentException("chunkIndex must be unique");
            }
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
