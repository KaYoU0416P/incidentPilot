package com.incidentpilot.agent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import com.incidentpilot.common.reliability.ReliabilitySettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用真实 Redis 验证运行状态写入、TTL 和读取；同时确认不持久化模型回答正文。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
class RedisAgentRunRecorderIntegrationTest {

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void writesReadableRunStateWithTtlAndWithoutAnswerBody() {
        var settings = new ReliabilitySettings(Duration.ofMinutes(5), 20, Duration.ofMinutes(1), 4,
                Duration.ofMinutes(2), Duration.ofSeconds(35));
        var recorder = new RedisAgentRunRecorder(redis, JsonMapper.builder().build(),
                Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC), settings);
        var runId = UUID.randomUUID();
        var diagnosis = new BoundedAgentService.AgentDiagnosis(runId,
                new QueryRouter.RouteDecision(QueryRouter.Route.AGENTIC, 0.9, "命中具体服务名，需要动态事实"),
                "秘密答案正文", List.of(new BoundedAgentService.ToolTrace("queryDeployment", "payment-service",
                        "SUCCESS", 2, 2, 12, null)),
                List.of(new BoundedAgentService.ToolEvidence("T1", "queryDeployment", "v3.2.1 | SUCCEEDED",
                        "deployment_record#service=payment-service", Instant.parse("2026-08-30T02:00:00Z"))),
                "REFERENCES_VALIDATED", 1, "TOOLS_COMPLETED", "ANSWERED", 987);

        try {
            recorder.record(diagnosis);

            var stored = recorder.find(runId);
            assertThat(stored).isPresent();
            assertThat(stored.get().terminalReason()).isEqualTo("ANSWERED");
            assertThat(stored.get().citationIds()).containsExactly("T1");
            assertThat(stored.get().traces()).singleElement()
                    .extracting(BoundedAgentService.ToolTrace::status).isEqualTo("SUCCESS");
            assertThat(stored.get().answerChars()).isEqualTo("秘密答案正文".length());
            assertThat(redis.opsForValue().get("incidentpilot:agent:run:" + runId)).doesNotContain("秘密答案正文");
            assertThat(redis.getExpire("incidentpilot:agent:run:" + runId)).isBetween(1L, 300L);
            assertThat(recorder.find(UUID.randomUUID())).isEmpty();
        } finally {
            redis.delete("incidentpilot:agent:run:" + runId);
        }
    }
}
