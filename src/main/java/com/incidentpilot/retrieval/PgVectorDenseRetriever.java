package com.incidentpilot.retrieval;

import com.incidentpilot.knowledge.embedding.TextEmbedder;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
@Profile("models")
public class PgVectorDenseRetriever implements Retriever {

    private final TextEmbedder embedder;
    private final JdbcClient jdbc;

    public PgVectorDenseRetriever(TextEmbedder embedder, JdbcClient jdbc) {
        this.embedder = embedder;
        this.jdbc = jdbc;
    }

    @Override
    public RetrievalResult retrieve(RetrievalQuery query) {
        if (query.topK() > 100) {
            throw new IllegalArgumentException("topK must not exceed 100");
        }

        float[] vector = embedder.embed(List.of(query.text())).getFirst();

        var chunks = jdbc.sql("""
                SELECT d.id AS source_id, c.id AS chunk_id,
                       c.content, d.source_uri,
                       1 - (c.embedding <=> CAST(:vector AS vector)) AS score
                FROM document_chunk c
                JOIN document d ON d.id = c.document_id
                WHERE c.embedding IS NOT NULL
                  AND c.embedding_model = :model
                ORDER BY c.embedding <=> CAST(:vector AS vector)
                LIMIT :topK
                """)
                .param("vector", Arrays.toString(vector))
                .param("model", "text-embedding-v4")
                .param("topK", query.topK())
                .query((rs, rowNum) -> new RetrievedChunk(
                        rs.getObject("source_id", UUID.class),
                        rs.getObject("chunk_id", UUID.class),
                        rs.getString("content"),
                        rs.getString("source_uri"),
                        rs.getDouble("score")
                ))
                .list();

        return new RetrievalResult("pgvector-dense", chunks);
    }
}