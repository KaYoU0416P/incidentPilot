package com.incidentpilot.agent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent 运行状态记录。写入失败不能影响诊断结果，因此实现必须自行降级。
 */
public interface AgentRunRecorder {
    void record(BoundedAgentService.AgentDiagnosis diagnosis);

    Optional<RunSnapshot> find(UUID runId);

    /**
     * 只保存可审计的运行元数据与工具轨迹，不保存模型回答正文，避免在缓存中复制模型输出。
     */
    record RunSnapshot(UUID runId, Instant recordedAt, String route, String routeReason, int steps,
                       String loopTermination, String terminalReason, String evidenceStatus, long latencyMs,
                       int answerChars, List<String> citationIds, List<BoundedAgentService.ToolTrace> traces) {

        static RunSnapshot of(BoundedAgentService.AgentDiagnosis diagnosis, Instant recordedAt) {
            return new RunSnapshot(diagnosis.runId(), recordedAt, diagnosis.route().route().name(),
                    diagnosis.route().reason(), diagnosis.steps(), diagnosis.loopTermination(),
                    diagnosis.terminalReason(), diagnosis.evidenceStatus(), diagnosis.latencyMs(),
                    diagnosis.answer() == null ? 0 : diagnosis.answer().length(),
                    diagnosis.citations().stream().map(BoundedAgentService.ToolEvidence::id).toList(),
                    diagnosis.traces());
        }
    }
}
