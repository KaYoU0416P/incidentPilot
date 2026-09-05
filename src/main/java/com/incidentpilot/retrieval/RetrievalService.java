package com.incidentpilot.retrieval;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("models")
public class RetrievalService {
    private final PgVectorDenseRetriever dense;
    private final PostgresLexicalRetriever lexical;
    private final HybridRetriever hybrid;
    private final LightweightRerankRetriever rerank;
    public RetrievalService(PgVectorDenseRetriever dense, PostgresLexicalRetriever lexical,
            HybridRetriever hybrid, LightweightRerankRetriever rerank) {
        this.dense = dense; this.lexical = lexical; this.hybrid = hybrid; this.rerank = rerank;
    }
    public RetrievalResult search(String text, int topK, String mode) {
        var query = new RetrievalQuery(text, topK);
        return switch (mode) {
            case "dense" -> dense.retrieve(query);
            case "lexical" -> lexical.retrieve(query);
            case "hybrid" -> hybrid.retrieve(query);
            case "hybrid-rerank" -> rerank.retrieve(query);
            default -> throw new IllegalArgumentException("mode must be dense, lexical, hybrid or hybrid-rerank");
        };
    }
}
