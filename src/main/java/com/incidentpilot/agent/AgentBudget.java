package com.incidentpilot.agent;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 单次运行的显式预算。所有值都是墙钟约束，不是 SLA 承诺。
 *
 * @param maxSteps      工具调用步数上限
 * @param totalBudget   进入 Agent 到返回的总预算
 * @param toolTimeout   单个工具调用上限
 * @param answerReserve 为最终回答保留的时间，剩余预算低于它就停止调用工具
 * @param contextChars  工具事实进入 prompt 的总字符预算
 * @param factChars     单条工具事实字符上限
 * @param factLookback  动态事实工具的显式时间窗口
 */
@ConfigurationProperties("incidentpilot.agent")
public record AgentBudget(int maxSteps, Duration totalBudget, Duration toolTimeout, Duration answerReserve,
                          int contextChars, int factChars, Duration factLookback) {

    public AgentBudget {
        if (maxSteps < 1) throw new IllegalArgumentException("maxSteps must be >= 1");
        if (contextChars < 1) throw new IllegalArgumentException("contextChars must be >= 1");
        if (factChars < 1) throw new IllegalArgumentException("factChars must be >= 1");
        if (totalBudget == null || totalBudget.isNegative() || totalBudget.isZero())
            throw new IllegalArgumentException("totalBudget must be positive");
        if (toolTimeout == null || toolTimeout.isNegative() || toolTimeout.isZero())
            throw new IllegalArgumentException("toolTimeout must be positive");
        if (answerReserve == null || answerReserve.isNegative())
            throw new IllegalArgumentException("answerReserve must be >= 0");
        if (factLookback == null || factLookback.isNegative() || factLookback.isZero())
            throw new IllegalArgumentException("factLookback must be positive");
        if (answerReserve.compareTo(totalBudget) >= 0)
            throw new IllegalArgumentException("answerReserve must be shorter than totalBudget");
    }

    public static AgentBudget defaults() {
        return new AgentBudget(5, Duration.ofSeconds(20), Duration.ofSeconds(6), Duration.ofSeconds(8),
                6000, 600, Duration.ofDays(90));
    }
}
