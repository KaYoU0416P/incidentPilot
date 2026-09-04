package com.incidentpilot.retrieval;

public record RetrievalQuery(String text, int topK) {

    public RetrievalQuery {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }

        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than 0");
        }

        text = text.strip();
    }
}
