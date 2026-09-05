package com.incidentpilot.common.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为每个请求建立 requestId，写入 MDC、响应头和当前线程上下文，使日志、审计和错误响应可关联。
 * 客户端传入的 requestId 必须匹配 UUID 形式，避免把任意头内容注入日志。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-Id";
    private static final Pattern UUID_SHAPE =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    public static String currentRequestId() {
        String value = CURRENT.get();
        return value != null ? value : MDC.get("requestId");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = incoming != null && UUID_SHAPE.matcher(incoming).matches()
                ? incoming : UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        CURRENT.set(requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            CURRENT.remove();
            MDC.remove("requestId");
        }
    }
}
