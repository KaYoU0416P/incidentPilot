package com.incidentpilot.agent;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;
import com.incidentpilot.retrieval.RetrievalService;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 五个只读诊断工具。动态事实工具强制要求 serviceName 与显式时间窗口，
 * 结果携带来源表与 UTC 时间戳，便于引用追溯。
 */
@Component
@Profile("models")
public class DiagnosticTools {
    private final List<DiagnosticTool> tools;

    public DiagnosticTools(RetrievalService retrieval, JdbcClient jdbc) {
        tools = List.of(
                new QueryTool("searchKnowledge", input -> DiagnosticTool.ToolResult.of("searchKnowledge",
                        retrieval.search(input.query(), input.limit(), "hybrid").chunks().stream()
                                .map(chunk -> new DiagnosticTool.ToolFact(chunk.content(), chunk.sourceLocator(), null))
                                .toList())),
                factTool("queryIncidentHistory", jdbc, "incident_record", "occurred_at",
                        "incident_key || ' | ' || summary"),
                factTool("queryDeployment", jdbc, "deployment_record", "deployed_at",
                        "version || ' | ' || status"),
                factTool("queryServiceStatus", jdbc, "service_status_snapshot", "observed_at",
                        "status || ' | ' || detail"),
                factTool("queryChangeLog", jdbc, "change_record", "changed_at",
                        "change_type || ' | ' || summary"));
    }

    public List<DiagnosticTool> all() { return tools; }

    private static DiagnosticTool factTool(String name, JdbcClient jdbc, String table, String timeColumn,
                                           String projection) {
        return new QueryTool(name, input -> {
            if (input.serviceName() == null) {
                return DiagnosticTool.ToolResult.skipped(name, "缺少 serviceName，拒绝对 " + table + " 执行宽表扫描");
            }
            var rows = jdbc.sql("SELECT " + projection + " AS fact, " + timeColumn + " AS fact_time FROM " + table
                            + " WHERE service_name = :service AND " + timeColumn + " >= :since"
                            + " ORDER BY " + timeColumn + " DESC LIMIT :limit")
                    .param("service", input.serviceName())
                    .param("since", OffsetDateTime.now(java.time.ZoneOffset.UTC).minus(input.lookback()))
                    .param("limit", input.limit())
                    .query((rs, row) -> new DiagnosticTool.ToolFact(
                            rs.getString("fact"),
                            table + "#service=" + input.serviceName(),
                            rs.getObject("fact_time", OffsetDateTime.class).toInstant()))
                    .list();
            return DiagnosticTool.ToolResult.of(name, rows);
        });
    }

    private record QueryTool(String name, Function<DiagnosticTool.ToolInput, DiagnosticTool.ToolResult> query)
            implements DiagnosticTool {
        public ToolResult execute(ToolInput input) { return query.apply(input); }
    }

    /** 便于测试与展示：把事实渲染为带时间与来源的一行文本。 */
    static String render(DiagnosticTool.ToolFact fact) {
        Instant at = fact.observedAt();
        return (at == null ? "" : at + " | ") + fact.source() + " | " + fact.text();
    }
}
