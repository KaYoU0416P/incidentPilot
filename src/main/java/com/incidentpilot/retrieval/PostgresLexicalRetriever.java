package com.incidentpilot.retrieval;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@Profile("models")
public class PostgresLexicalRetriever implements Retriever {
    private final JdbcClient jdbc;
    public PostgresLexicalRetriever(JdbcClient jdbc) { this.jdbc = jdbc; }
    public RetrievalResult retrieve(RetrievalQuery query) {
        if (query.topK() > 100) throw new IllegalArgumentException("topK must not exceed 100");
        var chunks = jdbc.sql("""
                SELECT d.id AS source_id, c.id AS chunk_id, c.content, d.source_uri,
                       ts_rank(c.search_vector, websearch_to_tsquery('simple', :query)) AS score
                FROM document_chunk c JOIN document d ON d.id = c.document_id
                WHERE c.search_vector @@ websearch_to_tsquery('simple', :query)
                ORDER BY score DESC, c.id LIMIT :topK
                """).param("query", query.text()).param("topK", query.topK())
                .query((rs, row) -> new RetrievedChunk(rs.getObject("source_id", UUID.class),
                        rs.getObject("chunk_id", UUID.class), rs.getString("content"),
                        rs.getString("source_uri"), rs.getDouble("score"))).list();
        return new RetrievalResult("postgres-lexical", chunks);
    }
}
