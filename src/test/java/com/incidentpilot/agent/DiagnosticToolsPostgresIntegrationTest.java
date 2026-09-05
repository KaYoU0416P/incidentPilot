package com.incidentpilot.agent;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import com.incidentpilot.retrieval.RetrievalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 验证动态事实工具真的按显式时间窗口过滤，并返回可追溯的来源与时间。
 * 只依赖 PostgreSQL，不构造模型 bean。
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
class DiagnosticToolsPostgresIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void deploymentToolReturnsOnlyRecordsInsideTheLookbackWindow() {
        String service = "integration-test-" + UUID.randomUUID().toString().substring(0, 8) + "-service";
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        insertDeployment(service, "v-recent", now.minusDays(3));
        insertDeployment(service, "v-stale", now.minusDays(400));

        var tools = new DiagnosticTools(mock(RetrievalService.class), jdbcClient);
        var deployment = tools.all().stream().filter(tool -> tool.name().equals("queryDeployment"))
                .findFirst().orElseThrow();

        var result = deployment.execute(new DiagnosticTool.ToolInput("最近部署", service, 5, Duration.ofDays(90)));

        assertThat(result.status()).isEqualTo(DiagnosticTool.ToolStatus.SUCCESS);
        assertThat(result.facts()).singleElement().satisfies(fact -> {
            assertThat(fact.text()).contains("v-recent").doesNotContain("v-stale");
            assertThat(fact.source()).isEqualTo("deployment_record#service=" + service);
            assertThat(fact.observedAt()).isAfter(Instant.now().minus(Duration.ofDays(4)));
        });
    }

    @Test
    void emptyWindowIsReportedAsEmptyRatherThanAsAFact() {
        String service = "integration-test-" + UUID.randomUUID().toString().substring(0, 8) + "-service";
        insertDeployment(service, "v-stale", OffsetDateTime.now(ZoneOffset.UTC).minusDays(400));

        var tools = new DiagnosticTools(mock(RetrievalService.class), jdbcClient);
        var deployment = tools.all().stream().filter(tool -> tool.name().equals("queryDeployment"))
                .findFirst().orElseThrow();

        var result = deployment.execute(new DiagnosticTool.ToolInput("最近部署", service, 5, Duration.ofDays(90)));

        assertThat(result.status()).isEqualTo(DiagnosticTool.ToolStatus.EMPTY);
        assertThat(result.facts()).isEmpty();
    }

    private void insertDeployment(String service, String version, OffsetDateTime deployedAt) {
        jdbcClient.sql("INSERT INTO deployment_record(service_name, version, deployed_at, status) "
                        + "VALUES (:service, :version, :deployedAt, 'SUCCEEDED')")
                .param("service", service).param("version", version).param("deployedAt", deployedAt).update();
    }
}
