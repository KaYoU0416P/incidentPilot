package com.incidentpilot.answer;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.incidentpilot.common.observability.RequestCorrelationFilter;
import com.incidentpilot.common.reliability.ReliabilitySettings;
import com.incidentpilot.common.reliability.RequestGuard;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 生命周期 SSE：事件为 {@code started -> completed}，错误路径为 {@code error}，不是逐 token streaming。
 *
 * <p>emitter 超时或客户端断开时，后台任务会被 {@code cancel(true)} 中断，并释放并发许可，
 * 避免继续占用连接与产生额外模型调用。中断能否立刻终止已发出的 HTTP 请求取决于底层客户端，
 * 本实现保证的是不再等待、不再写入已关闭的 emitter，且并发计数一定归还。
 */
@RestController
@Profile("models")
@RequestMapping("/api/v1/diagnoses/stream")
public class DiagnosisStreamController {
    private static final Logger log = LoggerFactory.getLogger(DiagnosisStreamController.class);

    private final RagService rag;
    private final ExecutorService executor;
    private final RequestGuard guard;
    private final MeterRegistry meters;
    private final ReliabilitySettings settings;

    public DiagnosisStreamController(RagService rag, ExecutorService executor, RequestGuard guard,
                                     MeterRegistry meters, ReliabilitySettings settings) {
        this.rag = rag;
        this.executor = executor;
        this.guard = guard;
        this.meters = meters;
        this.settings = settings;
    }

    @PostMapping
    public SseEmitter diagnose(@Valid @RequestBody DiagnosisController.Query request, HttpServletRequest http) {
        var lease = guard.acquire(RequestGuard.clientKey(http));
        String requestId = RequestCorrelationFilter.currentRequestId();
        var emitter = new SseEmitter(settings.sseTimeout().toMillis());
        var sequence = new AtomicInteger();
        var task = new AtomicReference<Future<?>>();

        Runnable cancel = () -> {
            Future<?> running = task.get();
            if (running != null) running.cancel(true);
            lease.close();
        };
        emitter.onTimeout(() -> {
            meters.counter("incidentpilot.sse.terminated", "reason", "TIMEOUT").increment();
            log.warn("SSE timed out, cancelling diagnosis requestId={}", requestId);
            cancel.run();
            emitter.complete();
        });
        emitter.onError(error -> {
            meters.counter("incidentpilot.sse.terminated", "reason", "DISCONNECTED").increment();
            cancel.run();
        });
        emitter.onCompletion(lease::close);

        task.set(executor.submit(() -> {
            try {
                emitter.send(event("started", sequence, requestId,
                        Map.of("mode", request.mode(), "topK", request.topK())));
                var diagnosis = rag.diagnose(request.query(), request.topK(), request.mode());
                if (Thread.currentThread().isInterrupted()) return;
                emitter.send(event("completed", sequence, requestId, diagnosis));
                meters.counter("incidentpilot.sse.terminated", "reason", "COMPLETED").increment();
                emitter.complete();
            } catch (Exception error) {
                if (Thread.currentThread().isInterrupted()) return;
                meters.counter("incidentpilot.sse.terminated", "reason", "ERROR").increment();
                log.error("SSE diagnosis failed requestId={}", requestId, error);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("requestId", String.valueOf(requestId), "message", "诊断未完成，请稍后重试。")));
                } catch (Exception ignored) {
                    // emitter 可能已关闭；错误已记录，无需再处理
                }
                emitter.completeWithError(error);
            } finally {
                lease.close();
            }
        }));
        return emitter;
    }

    private static SseEmitter.SseEventBuilder event(String name, AtomicInteger sequence, String requestId,
                                                    Object payload) {
        int number = sequence.incrementAndGet();
        return SseEmitter.event().name(name).id(String.valueOf(number))
                .data(Map.of("requestId", String.valueOf(requestId), "sequence", number, "payload", payload));
    }
}
