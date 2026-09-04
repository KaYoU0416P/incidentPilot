package com.incidentpilot.knowledge.ingestion;

import com.incidentpilot.knowledge.persistence.DocumentChunkEntity;
import com.incidentpilot.knowledge.persistence.DocumentChunkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
class DocumentIngestionPostgresIntegrationTest {

    @Autowired
    private DocumentIngestionService ingestionService;

    @Autowired
    private DocumentChunkRepository chunkRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void createsSkipsAndReplacesChunksAgainstPostgres() {
        String sourceKey = "integration-test:" + UUID.randomUUID();
        DocumentIngestionRequest firstRequest = request(
                sourceKey,
                new ChunkInput(0, "Check pool saturation.", Map.of("section", "diagnosis")),
                new ChunkInput(1, "Restart the worker if needed.", Map.of("section", "recovery"))
        );

        DocumentIngestionResult created = ingestionService.ingest(firstRequest);
        DocumentIngestionResult unchanged = ingestionService.ingest(firstRequest);
        DocumentIngestionResult replaced = ingestionService.ingest(request(
                sourceKey,
                new ChunkInput(0, "Increase the pool only after measuring demand.", Map.of("section", "recovery"))
        ));

        assertThat(created.status()).isEqualTo(DocumentIngestionStatus.CREATED);
        assertThat(unchanged.status()).isEqualTo(DocumentIngestionStatus.CONTENT_UNCHANGED);
        assertThat(replaced.status()).isEqualTo(DocumentIngestionStatus.CONTENT_REPLACED);
        assertThat(unchanged.documentId()).isEqualTo(created.documentId());
        assertThat(replaced.documentId()).isEqualTo(created.documentId());
        assertThat(chunkRepository.findAllByDocument_IdOrderByChunkIndexAsc(created.documentId()))
                .singleElement()
                .extracting(DocumentChunkEntity::getContent)
                .isEqualTo("Increase the pool only after measuring demand.");
        assertThat(chunkIsLexicallySearchable(created.documentId())).isTrue();
    }

    private DocumentIngestionRequest request(String sourceKey, ChunkInput... chunks) {
        return new DocumentIngestionRequest(
                sourceKey,
                "Payment timeout",
                "runbook",
                "payment-service",
                "docs/runbooks/payment-timeout.md",
                List.of(chunks)
        );
    }

    private boolean chunkIsLexicallySearchable(UUID documentId) {
        return jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM document_chunk
                            WHERE document_id = :documentId
                              AND search_vector @@ plainto_tsquery('simple', 'increase pool')
                              AND metadata @> '{"section":"recovery"}'::jsonb
                        )
                        """)
                .param("documentId", documentId)
                .query(Boolean.class)
                .single();
    }
}
