package com.incidentpilot.knowledge.embedding;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpringAiTextEmbedderTest {
    private final EmbeddingModel model = mock(EmbeddingModel.class);
    private final TextEmbedder embedder = new SpringAiTextEmbedder(model, 2);

    @Test
    void validatesAllInputsBeforeCallingProvider() {
        assertThat(embedder.embed(List.of())).isEmpty();
        assertThatThrownBy(() -> embedder.embed(List.of("valid", " "))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(model);
    }

    @Test
    void rejectsMissingOrDuplicateResults() {
        when(model.call(any())).thenReturn(new EmbeddingResponse(List.of()));
        assertThatThrownBy(() -> embedder.embed(List.of("a"))).isInstanceOf(IllegalStateException.class);
        when(model.call(any())).thenReturn(new EmbeddingResponse(List.of(
                new Embedding(new float[]{1, 0}, 0), new Embedding(new float[]{1, 0}, 0))));
        assertThatThrownBy(() -> embedder.embed(List.of("a", "b"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsWrongDimensionNonFiniteAndZeroVectors() {
        for (float[] vector : List.of(new float[]{1}, new float[]{Float.NaN, 1},
                new float[]{Float.POSITIVE_INFINITY, 1}, new float[]{0, 0})) {
            when(model.call(any())).thenReturn(new EmbeddingResponse(List.of(new Embedding(vector, 0))));
            assertThatThrownBy(() -> embedder.embed(List.of("a"))).isInstanceOf(IllegalStateException.class);
        }
    }
}
