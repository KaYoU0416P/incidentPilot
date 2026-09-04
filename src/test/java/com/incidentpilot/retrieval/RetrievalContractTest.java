package com.incidentpilot.retrieval;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RetrievalContractTest {

    @Test
    void retrievalQueryNormalizesTextAndKeepsTopK() {
        RetrievalQuery query = new RetrievalQuery("  database timeout  ", 5);

        assertThat(query.text()).isEqualTo("database timeout");
        assertThat(query.topK()).isEqualTo(5);
    }

    @Test
    void retrievalQueryRejectsInvalidInput() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RetrievalQuery("   ", 5));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RetrievalQuery("database timeout", 0));
    }

    @Test
    void retrievedChunkNormalizesTextAndRejectsNonFiniteScore() {
        UUID sourceId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();

        RetrievedChunk chunk = new RetrievedChunk(
                sourceId,
                chunkId,
                "  Connection pool was exhausted.  ",
                "  runbook/database#pool  ",
                0.82
        );

        assertThat(chunk.sourceId()).isEqualTo(sourceId);
        assertThat(chunk.chunkId()).isEqualTo(chunkId);
        assertThat(chunk.content()).isEqualTo("Connection pool was exhausted.");
        assertThat(chunk.sourceLocator()).isEqualTo("runbook/database#pool");
        assertThat(chunk.score()).isEqualTo(0.82);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RetrievedChunk(
                        sourceId,
                        chunkId,
                        "content",
                        "source",
                        Double.NaN
                ));
    }

    @Test
    void retrievalResultMakesAnImmutableSnapshot() {
        RetrievedChunk chunk = chunkWithScore(0.75);
        List<RetrievedChunk> mutableChunks = new ArrayList<>();
        mutableChunks.add(chunk);

        RetrievalResult result = new RetrievalResult("  dense-pgvector  ", mutableChunks);
        mutableChunks.clear();

        assertThat(result.retrieverName()).isEqualTo("dense-pgvector");
        assertThat(result.chunks()).containsExactly(chunk);
        assertThat(result.chunks()).isUnmodifiable();
    }

    @Test
    void retrievalResultRejectsNullElements() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RetrievalResult(
                        "dense-pgvector",
                        Collections.singletonList(null)
                ));
    }

    @Test
    void retrieverCanBeImplementedAsALambda() {
        RetrievedChunk chunk = chunkWithScore(0.91);
        Retriever retriever = query -> new RetrievalResult("test-retriever", List.of(chunk));

        RetrievalResult result = retriever.retrieve(new RetrievalQuery("timeout", 1));

        assertThat(result.retrieverName()).isEqualTo("test-retriever");
        assertThat(result.chunks()).containsExactly(chunk);
    }

    private RetrievedChunk chunkWithScore(double score) {
        return new RetrievedChunk(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Connection pool was exhausted.",
                "runbook/database#pool",
                score
        );
    }
}
