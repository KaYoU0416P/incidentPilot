package com.incidentpilot.agent;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 确定性路由：先判企业事实信号，再判解释型问题。
 * 顺序很重要——“payment-service 的 5xx 是什么原因”既含“是什么”也含服务名，必须走 AGENTIC。
 */
@Component
public class QueryRouter {
    static final Pattern SERVICE = Pattern.compile("([a-z][a-z0-9]*(?:-[a-z0-9]+)*-service)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VERSION = Pattern.compile("\\bv?\\d+\\.\\d+\\.\\d+\\b");
    private static final Pattern DYNAMIC_FACT =
            Pattern.compile("(发布|部署|上线|回滚|灰度|事故|故障记录|变更|当前|最近|历史|告警|监控|快照)");
    private static final Pattern EXPLANATION =
            Pattern.compile("(是什么|什么是|解释一下|解释下|请解释|的区别|有什么区别|原理|概念|为什么会)");

    public RouteDecision route(String query) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query is required");
        if (SERVICE.matcher(query).find()) return new RouteDecision(Route.AGENTIC, 0.90, "命中具体服务名，需要动态事实");
        if (VERSION.matcher(query).find()) return new RouteDecision(Route.AGENTIC, 0.85, "命中版本号，需要部署与变更事实");
        if (DYNAMIC_FACT.matcher(query).find()) return new RouteDecision(Route.AGENTIC, 0.80, "命中动态诊断事实关键词");
        if (EXPLANATION.matcher(query).find()) return new RouteDecision(Route.DIRECT, 0.75, "通用概念解释，不依赖企业事实");
        return new RouteDecision(Route.RETRIEVAL, 0.70, "优先检索企业知识");
    }

    public enum Route { DIRECT, RETRIEVAL, AGENTIC }

    public record RouteDecision(Route route, double confidence, String reason) { }
}
