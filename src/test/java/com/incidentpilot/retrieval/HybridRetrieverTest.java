package com.incidentpilot.retrieval;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HybridRetrieverTest {
    @Test void fusesRanksWithoutMixingRawScoresOrDoubleCounting() {
        var a = chunk(9999); var b = chunk(.001);
        var result = HybridRetriever.fuse(List.of(new RetrievalResult("dense",List.of(a,b)),
                new RetrievalResult("lexical",List.of(b,b))), 2);
        assertThat(result.chunks().getFirst().chunkId()).isEqualTo(b.chunkId());
        assertThat(result.chunks().getFirst().score()).isCloseTo(1.0/62 + 1.0/61, within(1e-12));
        assertThat(result.chunks().get(1).score()).isCloseTo(1.0/61, within(1e-12));
    }
    private RetrievedChunk chunk(double score) { return new RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(),"text","demo://test",score); }
}
