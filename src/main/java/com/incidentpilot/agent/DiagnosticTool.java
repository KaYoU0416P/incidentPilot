package com.incidentpilot.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 只读诊断工具契约。工具返回的数据是不可信内容，调用方负责编号、截断和引用校验。
 */
public interface DiagnosticTool {
    String name();

    ToolResult execute(ToolInput input);

    /**
     * 工具执行状态。只有 SUCCESS 的事实可以进入证据编号，其余状态只写入轨迹。
     */
    enum ToolStatus { SUCCESS, EMPTY, SKIPPED_MISSING_PARAMETER, TIMEOUT, FAILED }

    record ToolInput(String query, String serviceName, int limit, Duration lookback) {
        public ToolInput {
            if (query == null || query.isBlank()) throw new IllegalArgumentException("query is required");
            if (limit < 1 || limit > 20) throw new IllegalArgumentException("limit must be 1..20");
            if (lookback == null || lookback.isNegative() || lookback.isZero())
                throw new IllegalArgumentException("lookback must be positive");
            serviceName = serviceName == null || serviceName.isBlank() ? null : serviceName.strip();
        }
    }

    /** 单条工具事实：正文 + 可追溯来源 + 事实时间（动态事实必填，静态知识可为 null）。 */
    record ToolFact(String text, String source, Instant observedAt) {
        public ToolFact {
            if (text == null || text.isBlank()) throw new IllegalArgumentException("fact text is required");
            if (source == null || source.isBlank()) throw new IllegalArgumentException("fact source is required");
        }
    }

    record ToolResult(String tool, ToolStatus status, List<ToolFact> facts, String note) {
        public ToolResult {
            facts = List.copyOf(facts);
            if (status != ToolStatus.SUCCESS && !facts.isEmpty())
                throw new IllegalArgumentException("only SUCCESS results may carry facts");
        }

        public static ToolResult of(String tool, List<ToolFact> facts) {
            return facts.isEmpty()
                    ? new ToolResult(tool, ToolStatus.EMPTY, List.of(), "时间窗口内没有匹配记录")
                    : new ToolResult(tool, ToolStatus.SUCCESS, facts, null);
        }

        public static ToolResult skipped(String tool, String note) {
            return new ToolResult(tool, ToolStatus.SKIPPED_MISSING_PARAMETER, List.of(), note);
        }
    }
}
