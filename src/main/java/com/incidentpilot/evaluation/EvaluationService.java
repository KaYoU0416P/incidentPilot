package com.incidentpilot.evaluation;

import java.time.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import com.incidentpilot.demo.DemoService;
import com.incidentpilot.retrieval.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@Profile("models")
public class EvaluationService {
    private final DemoService demo;
    private final RetrievalService retrieval;
    private final JdbcTemplate jdbc;
    private final JsonMapper mapper;
    private final Clock clock;
    private final Path directory = Path.of("artifacts", "evaluations");
    public EvaluationService(DemoService demo, RetrievalService retrieval, JdbcTemplate jdbc, JsonMapper mapper, Clock clock) {
        this.demo = demo; this.retrieval = retrieval; this.jdbc = jdbc; this.mapper = mapper; this.clock = clock;
    }
    public synchronized Run run() throws IOException {
        var keys = jdbc.query("""
                SELECT d.source_key, c.id, c.embedding IS NOT NULL AS indexed
                FROM document d JOIN document_chunk c ON c.document_id = d.id
                WHERE d.source_key LIKE 'demo-v1:%'
                """, (rs, row) -> new IndexedChunk(rs.getString(1), rs.getObject(2, UUID.class), rs.getBoolean(3)));
        Map<String,Set<UUID>> ids = new HashMap<>();
        keys.forEach(k -> { if (k.indexed()) ids.computeIfAbsent(k.source(), ignored -> new HashSet<>()).add(k.id()); });
        if (demo.corpus().documents().stream().anyMatch(d -> !ids.containsKey(d.sourceKey()))) {
            throw new IllegalStateException("Seed the complete demo dataset before evaluation");
        }
        var cases = new ArrayList<CaseResult>();
        for (String mode : List.of("dense", "lexical", "hybrid", "hybrid-rerank")) {
            for (var item : demo.corpus().cases()) {
                var relevant = new HashSet<UUID>();
                item.relevantSourceKeys().forEach(key -> relevant.addAll(ids.getOrDefault(key, Set.of())));
                long start = System.nanoTime();
                var result = retrieval.search(item.query(), 3, mode);
                long millis = (System.nanoTime() - start) / 1_000_000;
                var metrics = RetrievalMetrics.calculate(result.chunks().stream().map(RetrievedChunk::chunkId).toList(), relevant, 3);
                cases.add(new CaseResult(item.id(), mode, item.query(), relevant, result, metrics, millis));
            }
        }
        var summaries = new ArrayList<Summary>();
        for (String mode : List.of("dense", "lexical", "hybrid", "hybrid-rerank")) {
            var applicable = cases.stream().filter(c -> c.mode().equals(mode) && !c.relevant().isEmpty()).toList();
            var latencies = cases.stream().filter(c -> c.mode().equals(mode)).map(CaseResult::latencyMs).sorted().toList();
            summaries.add(new Summary(mode, applicable.size(),
                    applicable.stream().mapToDouble(c -> c.metrics().recallAtK()).average().orElse(0),
                    applicable.stream().mapToDouble(c -> c.metrics().mrrAtK()).average().orElse(0),
                    applicable.stream().mapToDouble(c -> c.metrics().ndcgAtK()).average().orElse(0),
                    percentile(latencies, .50), percentile(latencies, .95)));
        }
        var run = new Run(UUID.randomUUID(), clock.instant(), demo.corpus().version(),
                "Synthetic 6-document dataset; 12 answerable and 2 no-answer cases. Binary relevance; no-answer metrics excluded. Small-sample functional comparison, not production benchmark.",
                "text-embedding-v4/1024; schema V3; cosine; planner-selected exact or HNSW; RRF k=60; lightweight rerank v1; topK=3; candidates=20; sequential calls",
                summaries, cases);
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, "run-", ".tmp");
        try { mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), run);
            Files.move(temp, directory.resolve(run.id() + ".json"), StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temp); }
        return run;
    }
    public Run read(UUID id) throws IOException { return mapper.readValue(directory.resolve(id + ".json").toFile(), Run.class); }
    private static long percentile(List<Long> values, double q) { return values.get(Math.max(0, (int)Math.ceil(values.size()*q)-1)); }
    private record IndexedChunk(String source, UUID id, boolean indexed) { }
    public record CaseResult(String caseId, String mode, String query, Set<UUID> relevant, RetrievalResult result,
                             RetrievalMetrics.Metrics metrics, long latencyMs) { }
    public record Summary(String mode, int answerableCases, double recallAt3, double mrrAt3, double ndcgAt3, long p50Ms, long p95Ms) { }
    public record Run(UUID id, Instant createdAt, String datasetVersion, String limitations, String configuration,
                      List<Summary> summaries, List<CaseResult> cases) { }
}
