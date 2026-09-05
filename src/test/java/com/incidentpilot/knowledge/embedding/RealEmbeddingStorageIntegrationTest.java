package com.incidentpilot.knowledge.embedding;

import java.util.UUID;
import com.incidentpilot.knowledge.ingestion.DocumentIngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("models")
@EnabledIfEnvironmentVariable(named = "RUN_MODEL_TESTS", matches = "true")
class RealEmbeddingStorageIntegrationTest {
    @Autowired DocumentIngestionService ingestion;
    @Autowired DocumentEmbeddingService indexing;
    @Autowired JdbcTemplate jdbc;

    @Test
    void writesRealBailianVectorIntoPostgres() {
        String source = "integration-test:real-index:" + UUID.randomUUID();
        UUID id = ingestion.ingest(DocumentEmbeddingIntegrationTest.request(source,
                "支付服务发布后连接池耗尽，应检查连接泄漏与超时配置。")).documentId();
        try {
            assertThat(indexing.index(id).status()).isEqualTo("INDEXED");
            assertThat(jdbc.queryForObject("SELECT vector_dims(embedding) FROM document_chunk WHERE document_id = ?",
                    Integer.class, id)).isEqualTo(1024);
            assertThat(jdbc.queryForObject("SELECT embedding_model FROM document_chunk WHERE document_id = ?",
                    String.class, id)).isEqualTo("text-embedding-v4");
            assertThat(indexing.index(id).status()).isEqualTo("ALREADY_INDEXED");
        } finally {
            jdbc.update("DELETE FROM document WHERE id = ? AND source_key = ?", id, source);
        }
    }
}
