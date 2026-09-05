package com.incidentpilot.evaluation;

import java.util.*;

public final class RetrievalMetrics {
    private RetrievalMetrics() { }
    public static Metrics calculate(List<UUID> ranked, Set<UUID> relevant, int k) {
        if (k < 1) throw new IllegalArgumentException("k must be positive");
        if (relevant.isEmpty()) return new Metrics(null, null, null);
        Set<UUID> seen = new HashSet<>();
        int hits = 0;
        double reciprocal = 0, dcg = 0, ideal = 0;
        for (int i = 0; i < Math.min(k, ranked.size()); i++) {
            UUID id = ranked.get(i);
            if (seen.add(id) && relevant.contains(id)) {
                hits++;
                if (reciprocal == 0) reciprocal = 1.0 / (i + 1);
                dcg += 1.0 / log2(i + 2);
            }
        }
        for (int i = 0; i < Math.min(k, relevant.size()); i++) ideal += 1.0 / log2(i + 2);
        return new Metrics((double) hits / relevant.size(), reciprocal, dcg / ideal);
    }
    private static double log2(double n) { return Math.log(n) / Math.log(2); }
    public record Metrics(Double recallAtK, Double mrrAtK, Double ndcgAtK) { }
}
