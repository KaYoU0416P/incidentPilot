package com.incidentpilot.knowledge.embedding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.util.Assert;

public final class SpringAiTextEmbedder implements TextEmbedder {
    private static final int BATCH_SIZE = 10;
    private final EmbeddingModel model;
    private final int dimensions;

    public SpringAiTextEmbedder(EmbeddingModel model, int dimensions) {
        Assert.notNull(model, "Embedding model is required");
        Assert.isTrue(dimensions > 0, "Dimensions must be positive");
        this.model = model;
        this.dimensions = dimensions;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        Assert.notNull(texts, "Texts are required");
        var inputs = List.copyOf(texts);
        inputs.forEach(text -> Assert.hasText(text, "Embedding text must not be blank"));
        var result = new ArrayList<float[]>(inputs.size());
        for (int start = 0; start < inputs.size(); start += BATCH_SIZE) {
            var batch = inputs.subList(start, Math.min(start + BATCH_SIZE, inputs.size()));
            var response = model.call(new EmbeddingRequest(batch, null));
            if (response == null || response.getResults().size() != batch.size()) {
                throw new IllegalStateException("Embedding response count mismatch");
            }
            var ordered = new float[batch.size()][];
            for (var embedding : response.getResults()) {
                int index = embedding.getIndex();
                if (index < 0 || index >= ordered.length || ordered[index] != null) {
                    throw new IllegalStateException("Invalid embedding response index");
                }
                float[] vector = embedding.getOutput();
                if (vector == null || vector.length != dimensions) {
                    throw new IllegalStateException("Embedding response dimension mismatch");
                }
                double norm = 0;
                for (float value : vector) {
                    if (!Float.isFinite(value)) {
                        throw new IllegalStateException("Non-finite embedding value");
                    }
                    norm += (double) value * value;
                }
                if (norm == 0) {
                    throw new IllegalStateException("Zero embedding vector");
                }
                ordered[index] = vector.clone();
            }
            result.addAll(Arrays.asList(ordered));
        }
        return List.copyOf(result);
    }
}
