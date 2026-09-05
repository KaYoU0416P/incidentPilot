package com.incidentpilot.demo;

import java.io.IOException;
import java.util.*;
import com.incidentpilot.knowledge.ingestion.*;
import com.incidentpilot.knowledge.embedding.DocumentEmbeddingService;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@Profile("models")
public class DemoService {
    private final Corpus corpus;
    private final DocumentIngestionService ingestion;
    private final DocumentEmbeddingService indexing;
    private final JdbcTemplate jdbc;
    public DemoService(JsonMapper mapper, DocumentIngestionService ingestion,
            DocumentEmbeddingService indexing, JdbcTemplate jdbc) throws IOException {
        try (var stream = new ClassPathResource("demo/corpus.json").getInputStream()) {
            corpus = mapper.readValue(stream, Corpus.class);
        }
        this.ingestion = ingestion; this.indexing = indexing; this.jdbc = jdbc;
    }
    public Corpus corpus() { return corpus; }
    public List<DocumentEmbeddingService.IndexResult> seed() {
        var results = new ArrayList<DocumentEmbeddingService.IndexResult>();
        for (var doc : corpus.documents()) {
            var stored = ingestion.ingest(new DocumentIngestionRequest(doc.sourceKey(), doc.title(), "runbook",
                    doc.serviceName(), doc.sourceUri(), List.of(new ChunkInput(0, doc.content(), Map.of("dataset", corpus.version())))));
            results.add(indexing.index(stored.documentId()));
        }
        seedDiagnosticFacts();
        return List.copyOf(results);
    }

    private void seedDiagnosticFacts() {
        jdbc.update("""
                INSERT INTO incident_record(incident_key, service_name, summary, occurred_at)
                VALUES ('demo-v1:INC-2026-091', 'payment-service',
                        'v3.2.1 发布后连接池耗尽，5xx 增加', TIMESTAMPTZ '2026-08-30 02:20:00+00')
                ON CONFLICT (incident_key) DO NOTHING
                """);
        if (jdbc.queryForObject("SELECT count(*) FROM deployment_record WHERE service_name='payment-service' AND version='v3.2.1'",
                Integer.class) == 0) {
            jdbc.update("INSERT INTO deployment_record(service_name,version,deployed_at,status) VALUES (?,?,?,?)",
                    "payment-service", "v3.2.1", java.sql.Timestamp.from(java.time.Instant.parse("2026-08-30T02:00:00Z")), "SUCCEEDED");
        }
        if (jdbc.queryForObject("SELECT count(*) FROM service_status_snapshot WHERE service_name='payment-service' AND detail LIKE 'demo-v1:%'",
                Integer.class) == 0) {
            jdbc.update("INSERT INTO service_status_snapshot(service_name,status,detail,observed_at) VALUES (?,?,?,?)",
                    "payment-service", "DEGRADED", "demo-v1: Hikari active=50 pending=37 max=50",
                    java.sql.Timestamp.from(java.time.Instant.parse("2026-08-30T02:25:00Z")));
        }
        if (jdbc.queryForObject("SELECT count(*) FROM change_record WHERE service_name='payment-service' AND summary LIKE 'demo-v1:%'",
                Integer.class) == 0) {
            jdbc.update("INSERT INTO change_record(service_name,change_type,summary,changed_at) VALUES (?,?,?,?)",
                    "payment-service", "CODE", "demo-v1: 将外部风控 HTTP 调用移入数据库事务",
                    java.sql.Timestamp.from(java.time.Instant.parse("2026-08-30T01:55:00Z")));
        }
    }
    /** 单独摄取一份文档，用于行为评测临时注入对抗性语料。 */
    public DocumentEmbeddingService.IndexResult seedDocument(DemoDocument doc) {
        var stored = ingestion.ingest(new DocumentIngestionRequest(doc.sourceKey(), doc.title(), "runbook",
                doc.serviceName(), doc.sourceUri(),
                List.of(new ChunkInput(0, doc.content(), Map.of("dataset", corpus.version() + "-adversarial")))));
        return indexing.index(stored.documentId());
    }

    public void removeDocument(String sourceKey) {
        jdbc.update("DELETE FROM document WHERE source_key = ?", sourceKey);
    }

    public record Corpus(String version, String notice, List<DemoDocument> documents, List<EvalCase> cases,
                         List<DemoDocument> adversarialDocuments, List<BehaviorCase> behaviorCases,
                         List<AgentBehaviorCase> agentBehaviorCases) { }
    public record DemoDocument(String sourceKey, String title, String serviceName, String sourceUri, String content) { }
    public record EvalCase(String id, String query, List<String> relevantSourceKeys) { }
    public record BehaviorCase(String id, String type, String query, String expectation, List<String> mustNotContain) { }
    public record AgentBehaviorCase(String id, String type, String query, String expectedRoute, String expectation) { }
}
