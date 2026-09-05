package com.incidentpilot.retrieval;

import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("models")
public class HybridRetriever implements Retriever {
    private final PgVectorDenseRetriever dense;
    private final PostgresLexicalRetriever lexical;
    public HybridRetriever(PgVectorDenseRetriever dense, PostgresLexicalRetriever lexical) {
        this.dense = dense; this.lexical = lexical;
    }
    public RetrievalResult retrieve(RetrievalQuery query) {
        if (query.topK() > 100) throw new IllegalArgumentException("topK must not exceed 100");
        var candidates = new RetrievalQuery(query.text(), Math.min(100, Math.max(20, query.topK() * 3)));
        return fuse(List.of(dense.retrieve(candidates), lexical.retrieve(candidates)), query.topK());
    }
    public static RetrievalResult fuse(List<RetrievalResult> lists, int topK) {
        Map<UUID, RetrievedChunk> evidence = new HashMap<>();
        Map<UUID, Double> scores = new HashMap<>();
        for (var list : lists) {
            Set<UUID> seen = new HashSet<>();
            for (int i = 0; i < list.chunks().size(); i++) {
                var chunk = list.chunks().get(i);
                if (seen.add(chunk.chunkId())) {
                    evidence.putIfAbsent(chunk.chunkId(), chunk);
                    scores.merge(chunk.chunkId(), 1.0 / (60 + i + 1), Double::sum);
                }
            }
        }
        var chunks = scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(topK).map(entry -> {
                    var c = evidence.get(entry.getKey());
                    return new RetrievedChunk(c.sourceId(), c.chunkId(), c.content(), c.sourceLocator(), entry.getValue());
                }).toList();
        return new RetrievalResult("hybrid-rrf-k60", chunks);
    }
}
