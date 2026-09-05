package com.incidentpilot.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.incidentpilot.retrieval.RetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DiagnosticToolsTest {
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final DiagnosticTools tools = new DiagnosticTools(mock(RetrievalService.class), jdbc);

    @Test
    void dynamicFactToolsRefuseWideScanAndNeverTouchTheDatabaseWithoutServiceName() {
        var input = new DiagnosticTool.ToolInput("最近发生了什么", null, 5, Duration.ofDays(90));

        for (var tool : tools.all().subList(1, tools.all().size())) {
            var result = tool.execute(input);
            assertThat(result.status()).isEqualTo(DiagnosticTool.ToolStatus.SKIPPED_MISSING_PARAMETER);
            assertThat(result.facts()).isEmpty();
            assertThat(result.note()).contains("拒绝").contains("宽表扫描");
        }
        verifyNoInteractions(jdbc);
    }

    @Test
    void toolResultRefusesToCarryFactsForNonSuccessStatus() {
        var fact = new DiagnosticTool.ToolFact("text", "source", Instant.EPOCH);
        assertThatThrownBy(() -> new DiagnosticTool.ToolResult("t", DiagnosticTool.ToolStatus.FAILED,
                List.of(fact), "note"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only SUCCESS");
    }

    @Test
    void emptyResultIsDistinctFromSuccess() {
        var result = DiagnosticTool.ToolResult.of("queryDeployment", List.of());
        assertThat(result.status()).isEqualTo(DiagnosticTool.ToolStatus.EMPTY);
        assertThat(result.note()).contains("时间窗口");
    }

    @Test
    void toolInputRequiresPositiveLookbackAndBoundedLimit() {
        assertThatThrownBy(() -> new DiagnosticTool.ToolInput("q", "payment-service", 5, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiagnosticTool.ToolInput("q", "payment-service", 99, Duration.ofDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renderedFactCarriesTimestampAndSource() {
        String rendered = DiagnosticTools.render(new DiagnosticTool.ToolFact("v3.2.1 | SUCCEEDED",
                "deployment_record#service=payment-service", Instant.parse("2026-08-30T02:00:00Z")));
        assertThat(rendered).startsWith("2026-08-30T02:00:00Z | deployment_record#service=payment-service | ");
    }
}
