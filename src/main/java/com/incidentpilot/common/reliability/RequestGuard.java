package com.incidentpilot.common.reliability;

import java.util.concurrent.atomic.AtomicBoolean;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于项目已有 Redis 的限流与并发控制。
 *
 * <p>限流是固定窗口计数器，不是令牌桶：实现简单、可测试，代价是窗口边界可能出现两倍瞬时速率。
 * 并发控制是全局计数器 + 兜底 TTL，防止进程异常退出后计数泄漏。
 *
 * <p>Redis 不可用时选择 fail-open：本地单实例 MVP 的可用性优先于配额精确性，
 * 该降级会记录日志与指标；面向公网部署应改为 fail-closed。
 */
@Component
@Profile("models")
public class RequestGuard {
    private static final Logger log = LoggerFactory.getLogger(RequestGuard.class);
    private static final String RATE_PREFIX = "incidentpilot:rate:";
    private static final String CONCURRENCY_KEY = "incidentpilot:concurrency:model-requests";

    private final StringRedisTemplate redis;
    private final ReliabilitySettings settings;
    private final MeterRegistry meters;

    public RequestGuard(StringRedisTemplate redis, ReliabilitySettings settings, MeterRegistry meters) {
        this.redis = redis;
        this.settings = settings;
        this.meters = meters;
    }

    /** 限流维度：本地演示按调用方 IP 计数，没有认证体系时不使用任何客户端自报头。 */
    public static String clientKey(jakarta.servlet.http.HttpServletRequest http) {
        String remote = http.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    /** 获取一次模型请求许可；被拒绝时抛出 {@link RequestRejectedException}。 */
    public Lease acquire(String clientKey) {
        checkRate(clientKey);
        acquireSlot();
        return new Lease();
    }

    private void checkRate(String clientKey) {
        String key = RATE_PREFIX + clientKey + ":" + System.currentTimeMillis() / settings.rateWindow().toMillis();
        Long used;
        try {
            used = redis.opsForValue().increment(key);
            if (used != null && used == 1L) redis.expire(key, settings.rateWindow());
        } catch (RuntimeException failure) {
            degrade("rate_limit", failure);
            return;
        }
        if (used != null && used > settings.ratePermits()) {
            meters.counter("incidentpilot.request.rejected", "reason", "RATE_LIMITED").increment();
            throw new RequestRejectedException("RATE_LIMITED",
                    "请求过于频繁，请稍后重试。每 " + settings.rateWindow().toSeconds() + " 秒最多 "
                            + settings.ratePermits() + " 次模型请求。");
        }
    }

    private void acquireSlot() {
        Long inFlight;
        try {
            inFlight = redis.opsForValue().increment(CONCURRENCY_KEY);
            if (inFlight != null && inFlight == 1L) redis.expire(CONCURRENCY_KEY, settings.concurrencyTtl());
        } catch (RuntimeException failure) {
            degrade("concurrency", failure);
            return;
        }
        if (inFlight != null && inFlight > settings.maxConcurrent()) {
            release();
            meters.counter("incidentpilot.request.rejected", "reason", "CONCURRENCY_LIMITED").increment();
            throw new RequestRejectedException("CONCURRENCY_LIMITED",
                    "并发诊断请求已达上限 " + settings.maxConcurrent() + "，请稍后重试。");
        }
    }

    private void release() {
        try {
            Long left = redis.opsForValue().decrement(CONCURRENCY_KEY);
            if (left != null && left < 0) redis.opsForValue().set(CONCURRENCY_KEY, "0", settings.concurrencyTtl());
        } catch (RuntimeException failure) {
            degrade("concurrency_release", failure);
        }
    }

    private void degrade(String stage, RuntimeException failure) {
        meters.counter("incidentpilot.request.guard_degraded", "stage", stage).increment();
        log.warn("Redis guard unavailable at {}, failing open: {}", stage, failure.getClass().getSimpleName());
    }

    /** 并发许可。释放是幂等的，可安全放在 try-with-resources 与异步回调中重复调用。 */
    public final class Lease implements AutoCloseable {
        private final AtomicBoolean released = new AtomicBoolean();

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) release();
        }
    }
}
