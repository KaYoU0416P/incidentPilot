package com.incidentpilot.evaluation;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RetrievalMetricsTest {
    @Test void computesKnownRankingAndHandlesDuplicates() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),wrong=UUID.randomUUID();
        var metrics = RetrievalMetrics.calculate(List.of(wrong,a,b),Set.of(a,b),3);
        assertThat(metrics.recallAtK()).isEqualTo(1);
        assertThat(metrics.mrrAtK()).isEqualTo(.5);
        double expected=(1/(Math.log(3)/Math.log(2))+.5)/(1+1/(Math.log(3)/Math.log(2)));
        assertThat(metrics.ndcgAtK()).isCloseTo(expected,within(1e-12));
        assertThat(RetrievalMetrics.calculate(List.of(a,a),Set.of(a,b),2).recallAtK()).isEqualTo(.5);
        assertThat(RetrievalMetrics.calculate(List.of(a),Set.of(),3).recallAtK()).isNull();
        assertThat(RetrievalMetrics.calculate(List.of(),Set.of(a),3).mrrAtK()).isZero();
    }
}
