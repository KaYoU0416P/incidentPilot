package com.incidentpilot.common.reliability;

import java.time.Duration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestGuardTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final ReliabilitySettings settings =
            new ReliabilitySettings(Duration.ofHours(1), 2, Duration.ofMinutes(1), 2, Duration.ofMinutes(2),
                    Duration.ofSeconds(35));
    private final RequestGuard guard = new RequestGuard(redis, settings, meters);

    @Test
    void rejectsWhenFixedWindowPermitsAreExceeded() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(3L);

        assertThatThrownBy(() -> guard.acquire("127.0.0.1"))
                .isInstanceOf(RequestRejectedException.class)
                .hasMessageContaining("请求过于频繁");
        assertThat(meters.counter("incidentpilot.request.rejected", "reason", "RATE_LIMITED").count()).isEqualTo(1);
    }

    @Test
    void rejectsAndReturnsSlotWhenConcurrencyLimitIsExceeded() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(startsWithRate())).thenReturn(1L);
        when(values.increment(eq("incidentpilot:concurrency:model-requests"))).thenReturn(3L);
        when(values.decrement(eq("incidentpilot:concurrency:model-requests"))).thenReturn(2L);

        assertThatThrownBy(() -> guard.acquire("127.0.0.1"))
                .isInstanceOf(RequestRejectedException.class)
                .hasMessageContaining("并发诊断请求已达上限");
        verify(values).decrement("incidentpilot:concurrency:model-requests");
    }

    @Test
    void leaseReleasesConcurrencySlotExactlyOnceEvenIfClosedTwice() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(1L);
        when(values.decrement(anyString())).thenReturn(0L);

        var lease = guard.acquire("127.0.0.1");
        lease.close();
        lease.close();

        verify(values).decrement("incidentpilot:concurrency:model-requests");
    }

    @Test
    void failsOpenAndRecordsDegradationWhenRedisIsUnavailable() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> guard.acquire("127.0.0.1").close()).doesNotThrowAnyException();
        assertThat(meters.counter("incidentpilot.request.guard_degraded", "stage", "rate_limit").count())
                .isGreaterThanOrEqualTo(1);
        verify(values, atLeastOnce()).increment(anyString());
    }

    private static String startsWithRate() {
        return org.mockito.ArgumentMatchers.argThat(key -> key != null && key.startsWith("incidentpilot:rate:"));
    }
}
