package com.incidentpilot.agent;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import com.incidentpilot.common.reliability.ReliabilitySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 用项目已有 Redis 保存带 TTL 的 Agent 运行状态。
 * Redis 不可用时只降级为不记录，绝不影响诊断结果本身。
 */
@Component
@Profile("models")
public class RedisAgentRunRecorder implements AgentRunRecorder {
    private static final Logger log = LoggerFactory.getLogger(RedisAgentRunRecorder.class);
    private static final String PREFIX = "incidentpilot:agent:run:";

    private final StringRedisTemplate redis;
    private final JsonMapper mapper;
    private final Clock clock;
    private final ReliabilitySettings settings;

    public RedisAgentRunRecorder(StringRedisTemplate redis, JsonMapper mapper, Clock clock,
                                 ReliabilitySettings settings) {
        this.redis = redis;
        this.mapper = mapper;
        this.clock = clock;
        this.settings = settings;
    }

    @Override
    public void record(BoundedAgentService.AgentDiagnosis diagnosis) {
        try {
            var snapshot = RunSnapshot.of(diagnosis, clock.instant());
            redis.opsForValue().set(PREFIX + diagnosis.runId(), mapper.writeValueAsString(snapshot),
                    settings.runRetention());
        } catch (RuntimeException failure) {
            log.warn("Agent run state not recorded, continuing without it: {}", failure.getClass().getSimpleName());
        }
    }

    @Override
    public Optional<RunSnapshot> find(UUID runId) {
        try {
            String payload = redis.opsForValue().get(PREFIX + runId);
            return payload == null ? Optional.empty() : Optional.of(mapper.readValue(payload, RunSnapshot.class));
        } catch (RuntimeException failure) {
            log.warn("Agent run state not readable: {}", failure.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
