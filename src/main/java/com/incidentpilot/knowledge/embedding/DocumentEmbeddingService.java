package com.incidentpilot.knowledge.embedding;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("models")
public class DocumentEmbeddingService {
    public static final String MODEL = "text-embedding-v4";
    public static final int DIMENSIONS = 1024;
    private final JdbcTemplate jdbc;
    private final TextEmbedder embedder;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public DocumentEmbeddingService(JdbcTemplate jdbc, TextEmbedder embedder,
            PlatformTransactionManager manager, Clock clock) {
        this.jdbc = jdbc;
        this.embedder = embedder;
        this.transaction = new TransactionTemplate(manager);
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.NEVER)
    public IndexResult index(UUID documentId) {
        if (documentId == null) throw new IllegalArgumentException("documentId is required");
        var snapshot = jdbc.query("""
                SELECT d.content_hash, c.id, c.content,
                       (c.embedding IS NOT NULL AND c.embedding_model = ?) AS indexed
                FROM document d JOIN document_chunk c ON c.document_id = d.id
                WHERE d.id = ? ORDER BY c.chunk_index
                """, (rs, row) -> new ChunkSnapshot(rs.getString("content_hash"),
                rs.getObject("id", UUID.class), rs.getString("content"), rs.getBoolean("indexed")),
                MODEL, documentId);
        if (snapshot.isEmpty()) throw new IllegalArgumentException("Document has no chunks or does not exist");
        if (snapshot.stream().allMatch(ChunkSnapshot::indexed)) {
            return new IndexResult(documentId, snapshot.size(), "ALREADY_INDEXED");
        }
        var vectors = embedder.embed(snapshot.stream().map(ChunkSnapshot::content).toList());
        if (vectors.size() != snapshot.size()) throw new IllegalStateException("Embedding count mismatch");
        var serialized = vectors.stream().map(DocumentEmbeddingService::vectorLiteral).toList();
        transaction.executeWithoutResult(status -> {
            var hashes = jdbc.queryForList("SELECT content_hash FROM document WHERE id = ? FOR UPDATE",
                    String.class, documentId);
            if (hashes.isEmpty() || !snapshot.getFirst().hash().equals(hashes.getFirst())) {
                throw new IllegalStateException("Document changed during embedding; retry indexing");
            }
            Timestamp now = Timestamp.from(clock.instant());
            var arguments = java.util.stream.IntStream.range(0, snapshot.size())
                    .mapToObj(i -> new Object[]{serialized.get(i), MODEL, now, snapshot.get(i).id(), documentId}).toList();
            int[] counts = jdbc.batchUpdate("""
                    UPDATE document_chunk SET embedding = ?::vector, embedding_model = ?, updated_at = ?
                    WHERE id = ? AND document_id = ?
                    """, arguments);
            if (Arrays.stream(counts).anyMatch(count -> count != 1)) {
                throw new IllegalStateException("Chunks changed during embedding; retry indexing");
            }
        });
        return new IndexResult(documentId, snapshot.size(), "INDEXED");
    }

    public static String vectorLiteral(float[] vector) {
        if (vector == null || vector.length != DIMENSIONS) throw new IllegalArgumentException("Expected 1024 dimensions");
        double norm = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Vector must contain finite values");
            norm += (double) value * value;
        }
        if (norm == 0) throw new IllegalArgumentException("Vector must not be zero");
        return Arrays.toString(vector);
    }

    private record ChunkSnapshot(String hash, UUID id, String content, boolean indexed) { }
    public record IndexResult(UUID documentId, int chunkCount, String status) { }
}
