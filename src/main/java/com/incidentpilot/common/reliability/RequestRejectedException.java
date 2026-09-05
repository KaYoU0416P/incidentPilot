package com.incidentpilot.common.reliability;

/** 限流或并发控制拒绝请求；对外返回 429，不泄漏内部计数细节。 */
public class RequestRejectedException extends RuntimeException {
    private final String code;

    public RequestRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
