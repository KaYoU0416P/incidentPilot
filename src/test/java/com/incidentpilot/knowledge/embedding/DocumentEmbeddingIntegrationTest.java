package com.incidentpilot.knowledge.embedding;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import com.incidentpilot.knowledge.ingestion.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(DocumentEmbeddingIntegrationTest.Config.class)
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
class DocumentEmbeddingIntegrationTest {
    @Autowired DocumentIngestionService ingestion;
    @Autowired DocumentEmbeddingService indexing;
    @Autowired JdbcTemplate jdbc;
    @Autowired Probe probe;

    @TestConfiguration
    static class Config {
        @Bean Probe probe() { return new Probe(); }
        @Bean DocumentEmbeddingService indexing(JdbcTemplate jdbc, Probe probe,
                PlatformTransactionManager manager, Clock clock) {
            return new DocumentEmbeddingService(jdbc, probe, manager, clock);
        }
    }

    static class Probe implements TextEmbedder {
        AtomicInteger calls = new AtomicInteger();
        Runnable duringCall = () -> {};
        public List<float[]> embed(List<String> texts) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            calls.incrementAndGet();
            duringCall.run();
            return texts.stream().map(text -> {
                float[] vector = new float[1024]; vector[0] = 1; return vector;
            }).toList();
        }
    }

    @Test
    void storesSkipsAndRejectsStaleEmbeddings() {
        String source = "integration-test:index:" + UUID.randomUUID();
        UUID id = ingestion.ingest(request(source, "first evidence")).documentId();
        probe.calls.set(0);
        try {
            assertThat(indexing.index(id).status()).isEqualTo("INDEXED");
            assertThat(jdbc.queryForObject("SELECT vector_dims(embedding) FROM document_chunk WHERE document_id = ?",
                    Integer.class, id)).isEqualTo(1024);
            assertThat(indexing.index(id).status()).isEqualTo("ALREADY_INDEXED");
            assertThat(probe.calls.get()).isEqualTo(1);
            ingestion.ingest(request(source, "second evidence"));
            probe.duringCall = () -> ingestion.ingest(request(source, "third evidence"));
            assertThatThrownBy(() -> indexing.index(id)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Document changed");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM document_chunk WHERE document_id = ? AND embedding IS NOT NULL",
                    Integer.class, id)).isZero();
            probe.duringCall = () -> {};
            assertThat(indexing.index(id).status()).isEqualTo("INDEXED");
        } finally {
            probe.duringCall = () -> {};
            jdbc.update("DELETE FROM document WHERE id = ? AND source_key = ?", id, source);
        }
    }

    static DocumentIngestionRequest request(String source, String content) {
        return new DocumentIngestionRequest(source, "Index test", "runbook", "payment-service",
                "test://index", List.of(new ChunkInput(0, content, Map.of())));
    }
}
