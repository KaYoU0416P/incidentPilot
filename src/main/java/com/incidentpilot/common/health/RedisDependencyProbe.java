package com.incidentpilot.common.health;

import java.util.Objects;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
class RedisDependencyProbe implements DependencyProbe {

    private final StringRedisTemplate redisTemplate;

    RedisDependencyProbe(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String name() {
        return "redis";
    }

    @Override
    public DependencyStatus check() {
        try (RedisConnection connection = Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection()) {
            String result = connection.ping();
            return "PONG".equalsIgnoreCase(result)
                    ? DependencyStatus.up("PONG")
                    : new DependencyStatus("DOWN", "unexpected probe result");
        } catch (RuntimeException exception) {
            return DependencyStatus.down(exception);
        }
    }
}
