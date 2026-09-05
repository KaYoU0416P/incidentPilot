package com.incidentpilot.agent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import com.incidentpilot.answer.AnswerGenerator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoundedAgentServiceTest {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AgentRunRecorder recorder = new AgentRunRecorder() {
        public void record(BoundedAgentService.AgentDiagnosis diagnosis) { }
        public Optional<RunSnapshot> find(UUID runId) { return Optional.empty(); }
    };

    @AfterEach
    void shutdown() { executor.shutdownNow(); }

    @Test
    void agenticRouteRunsFiveUniqueReadOnlyToolsAndAnswers() {
        var service = service(AgentBudget.defaults(), fixedClock(),
                IntStream.rangeClosed(1, 5).mapToObj(index -> factTool("tool" + index)).toList(), "diagnosis [T1]");

        var result = service.diagnose("payment-service 发布后状态异常");

        assertThat(result.steps()).isEqualTo(5);
        assertThat(result.loopTermination()).isEqualTo("TOOLS_COMPLETED");
        assertThat(result.terminalReason()).isEqualTo("ANSWERED");
        assertThat(result.evidenceStatus()).isEqualTo("REFERENCES_VALIDATED");
        assertThat(result.citations()).singleElement()
                .extracting(BoundedAgentService.ToolEvidence::id).isEqualTo("T1");
        assertThat(result.traces()).extracting(BoundedAgentService.ToolTrace::tool)
                .containsExactly("tool1", "tool2", "tool3", "tool4", "tool5");
        assertThat(result.traces()).allSatisfy(trace -> assertThat(trace.status()).isEqualTo("SUCCESS"));
        assertThat(result.runId()).isNotNull();
    }

    @Test
    void invalidToolReferenceIsRejected() {
        var service = service(AgentBudget.defaults(), fixedClock(), List.of(factTool("knowledge")),
                "unsupported [T99]");

        var result = service.diagnose("连接池耗尽怎么排查");

        assertThat(result.terminalReason()).isEqualTo("INVALID_REFERENCES");
        assertThat(result.citations()).isEmpty();
    }

    @Test
    void explicitRefusalIsNotUpgradedToValidatedByIncidentalReferences() {
        var service = service(AgentBudget.defaults(), fixedClock(), List.of(factTool("knowledge")),
                "证据不足。工具事实 [T1] 与问题无关。");

        var result = service.diagnose("连接池耗尽怎么排查");

        assertThat(result.terminalReason()).isEqualTo("MODEL_REFUSED");
        assertThat(result.evidenceStatus()).isEqualTo("INSUFFICIENT");
        assertThat(result.citations()).isEmpty();
    }

    @Test
    void directRouteAnswersWithoutToolsAndWithoutCitations() {
        var service = service(AgentBudget.defaults(), fixedClock(), List.of(factTool("knowledge")),
                "本次未使用企业内部数据。连接池是复用连接的池化组件。");

        var result = service.diagnose("什么是连接池");

        assertThat(result.route().route()).isEqualTo(QueryRouter.Route.DIRECT);
        assertThat(result.terminalReason()).isEqualTo("DIRECT_ANSWERED");
        assertThat(result.evidenceStatus()).isEqualTo("NO_ENTERPRISE_EVIDENCE");
        assertThat(result.steps()).isZero();
        assertThat(result.traces()).isEmpty();
        assertThat(result.answer()).contains("本次未使用企业内部数据");
    }

    @Test
    void slowToolIsCancelledAtItsOwnTimeoutAndProducesNoEvidence() {
        var budget = new AgentBudget(5, Duration.ofSeconds(20), Duration.ofMillis(120), Duration.ofSeconds(8),
                6000, 600, Duration.ofDays(90));
        var service = service(budget, fixedClock(), List.of(new SlowTool("slowTool"), factTool("fastTool")),
                "diagnosis [T1]");

        var result = service.diagnose("payment-service 发布后状态异常");

        assertThat(result.traces()).hasSize(2);
        assertThat(result.traces().getFirst().status()).isEqualTo("TIMEOUT");
        assertThat(result.traces().getFirst().acceptedFacts()).isZero();
        assertThat(result.traces().getFirst().note()).contains("已取消");
        assertThat(result.terminalReason()).isEqualTo("ANSWERED");
        assertThat(result.citations()).singleElement()
                .extracting(BoundedAgentService.ToolEvidence::tool).isEqualTo("fastTool");
    }

    @Test
    void loopStopsWhenRemainingBudgetDropsBelowAnswerReserve() {
        var clock = new SteppingClock(Instant.parse("2026-09-05T00:00:00Z"), Duration.ofSeconds(5));
        var service = service(AgentBudget.defaults(), clock,
                IntStream.rangeClosed(1, 5).mapToObj(index -> factTool("tool" + index)).toList(), "diagnosis [T1]");

        var result = service.diagnose("payment-service 发布后状态异常");

        assertThat(result.steps()).isLessThan(5);
        assertThat(result.loopTermination()).isEqualTo("DEADLINE_EXCEEDED");
    }

    @Test
    void contextBudgetBoundsToolFactsInsteadOfGrowingWithoutLimit() {
        var budget = new AgentBudget(5, Duration.ofSeconds(20), Duration.ofSeconds(6), Duration.ofSeconds(8),
                200, 50, Duration.ofDays(90));
        var service = service(budget, fixedClock(),
                IntStream.rangeClosed(1, 5).mapToObj(index -> longFactTool("tool" + index)).toList(), "diagnosis [T1]");

        var result = service.diagnose("payment-service 发布后状态异常");

        assertThat(result.loopTermination()).isEqualTo("CONTEXT_BUDGET_EXHAUSTED");
        assertThat(result.traces().stream().mapToInt(BoundedAgentService.ToolTrace::acceptedFacts).sum())
                .isLessThanOrEqualTo(4);
    }

    @Test
    void toolWithoutServiceNameIsSkippedAndItsNoteNeverBecomesEvidence() {
        var service = service(AgentBudget.defaults(), fixedClock(),
                List.of(new SkippingTool("queryDeployment")), "diagnosis [T1]");

        var result = service.diagnose("最近的变更有哪些");

        assertThat(result.traces()).singleElement().satisfies(trace -> {
            assertThat(trace.status()).isEqualTo("SKIPPED_MISSING_PARAMETER");
            assertThat(trace.acceptedFacts()).isZero();
        });
        assertThat(result.terminalReason()).isEqualTo("NO_EVIDENCE");
        assertThat(result.evidenceStatus()).isEqualTo("INSUFFICIENT");
    }

    @Test
    void failingToolIsRecordedWithoutLeakingStackDetails() {
        var service = service(AgentBudget.defaults(), fixedClock(),
                List.of(new FailingTool("queryServiceStatus"), factTool("fastTool")), "diagnosis [T1]");

        var result = service.diagnose("payment-service 发布后状态异常");

        assertThat(result.traces().getFirst().status()).isEqualTo("FAILED");
        assertThat(result.traces().getFirst().note()).doesNotContain("secret").contains("IllegalStateException");
        assertThat(result.terminalReason()).isEqualTo("ANSWERED");
    }

    private BoundedAgentService service(AgentBudget budget, Clock clock, List<DiagnosticTool> available,
                                        String answer) {
        var tools = mock(DiagnosticTools.class);
        var generator = mock(AnswerGenerator.class);
        when(tools.all()).thenReturn(available);
        when(generator.generate(anyString(), anyString())).thenReturn(answer);
        return new BoundedAgentService(new QueryRouter(), tools, generator, clock, executor, budget, recorder,
                new SimpleMeterRegistry());
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC);
    }

    private static DiagnosticTool factTool(String name) {
        return new StubTool(name, new DiagnosticTool.ToolResult(name, DiagnosticTool.ToolStatus.SUCCESS,
                List.of(new DiagnosticTool.ToolFact("fact", name + "#source", Instant.parse("2026-09-01T00:00:00Z"))),
                null));
    }

    private static DiagnosticTool longFactTool(String name) {
        return new StubTool(name, new DiagnosticTool.ToolResult(name, DiagnosticTool.ToolStatus.SUCCESS,
                List.of(new DiagnosticTool.ToolFact("x".repeat(400), name + "#source",
                        Instant.parse("2026-09-01T00:00:00Z"))), null));
    }

    private record StubTool(String name, ToolResult result) implements DiagnosticTool {
        public ToolResult execute(ToolInput input) { return result; }
    }

    private record SkippingTool(String name) implements DiagnosticTool {
        public ToolResult execute(ToolInput input) {
            return ToolResult.skipped(name, "缺少 serviceName，拒绝对 deployment_record 执行宽表扫描");
        }
    }

    private record FailingTool(String name) implements DiagnosticTool {
        public ToolResult execute(ToolInput input) { throw new IllegalStateException("connection refused"); }
    }

    private record SlowTool(String name) implements DiagnosticTool {
        public ToolResult execute(ToolInput input) {
            try { Thread.sleep(Duration.ofSeconds(5)); } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return new ToolResult(name, ToolStatus.SUCCESS,
                    List.of(new ToolFact("late", name + "#source", Instant.parse("2026-09-01T00:00:00Z"))), null);
        }
    }

    /** 每次读时间都前进固定步长，用于验证预算耗尽会真正终止循环。 */
    private static final class SteppingClock extends Clock {
        private final Duration step;
        private Instant now;

        private SteppingClock(Instant start, Duration step) { this.now = start; this.step = step; }

        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { Instant current = now; now = now.plus(step); return current; }
    }
}
