package com.incidentpilot.retrieval;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("models")
public class LightweightRerankRetriever implements Retriever {
    private static final Pattern ALPHANUMERIC = Pattern.compile("[a-z0-9][a-z0-9._-]*");
    private final HybridRetriever hybrid;

    public LightweightRerankRetriever(HybridRetriever hybrid) { this.hybrid = hybrid; }

    @Override
    public RetrievalResult retrieve(RetrievalQuery query) {
        if (query.topK() > 100) throw new IllegalArgumentException("topK must not exceed 100");
        var candidates = hybrid.retrieve(new RetrievalQuery(query.text(), Math.min(100, Math.max(20, query.topK() * 3))));
        Set<String> queryTerms = terms(query.text());
        double maxRrf = candidates.chunks().stream().mapToDouble(RetrievedChunk::score).max().orElse(1);
        var ranked = candidates.chunks().stream().map(chunk -> {
            Set<String> contentTerms = terms(chunk.content());
            long hits = queryTerms.stream().filter(contentTerms::contains).count();
            double overlap = queryTerms.isEmpty() ? 0 : (double) hits / queryTerms.size();
            double score = 0.8 * overlap + 0.2 * (chunk.score() / maxRrf);
            return new RetrievedChunk(chunk.sourceId(), chunk.chunkId(), chunk.content(), chunk.sourceLocator(), score);
        }).sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed()
                .thenComparing(RetrievedChunk::chunkId)).limit(query.topK()).toList();
        return new RetrievalResult("hybrid-lightweight-rerank-v1", ranked);
    }

    static Set<String> terms(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        Set<String> terms = new HashSet<>();
        var matcher = ALPHANUMERIC.matcher(normalized);
        while (matcher.find()) terms.add(matcher.group());
        String chinese = normalized.replaceAll("[^\\p{IsHan}]", "");
        for (int i = 0; i + 1 < chinese.length(); i++) terms.add(chinese.substring(i, i + 2));
        return terms;
    }
}
