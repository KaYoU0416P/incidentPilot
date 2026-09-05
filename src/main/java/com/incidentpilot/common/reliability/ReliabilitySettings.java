package com.incidentpilot.common.reliability;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地单实例可靠性参数。数值是为本机演示选的保守值，不是压测结论。
 *
 * @param runRetention   Agent 运行状态在 Redis 的保留时间
 * @param ratePermits    单个客户端在一个窗口内允许的模型请求数
 * @param rateWindow     固定窗口长度
 * @param maxConcurrent  同时在途的模型诊断请求数上限
 * @param concurrencyTtl 并发计数键的兜底 TTL，防止进程异常退出后计数泄漏
 * @param sseTimeout     SSE emitter 超时；超时会取消后台诊断任务并归还并发许可
 */
@ConfigurationProperties("incidentpilot.reliability")
public record ReliabilitySettings(Duration runRetention, int ratePermits, Duration rateWindow,
                                  int maxConcurrent, Duration concurrencyTtl, Duration sseTimeout) {

    public ReliabilitySettings {
        if (sseTimeout == null || sseTimeout.isNegative() || sseTimeout.isZero())
            throw new IllegalArgumentException("sseTimeout must be positive");
        if (runRetention == null || runRetention.isNegative() || runRetention.isZero())
            throw new IllegalArgumentException("runRetention must be positive");
        if (ratePermits < 1) throw new IllegalArgumentException("ratePermits must be >= 1");
        if (rateWindow == null || rateWindow.isNegative() || rateWindow.isZero())
            throw new IllegalArgumentException("rateWindow must be positive");
        if (maxConcurrent < 1) throw new IllegalArgumentException("maxConcurrent must be >= 1");
        if (concurrencyTtl == null || concurrencyTtl.isNegative() || concurrencyTtl.isZero())
            throw new IllegalArgumentException("concurrencyTtl must be positive");
    }

    public static ReliabilitySettings defaults() {
        return new ReliabilitySettings(Duration.ofHours(1), 20, Duration.ofMinutes(1), 4, Duration.ofMinutes(2),
                Duration.ofSeconds(35));
    }
}
