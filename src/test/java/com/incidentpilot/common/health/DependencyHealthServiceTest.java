package com.incidentpilot.common.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DependencyHealthServiceTest {

    @Test
    void reportsUpWhenEveryDependencyProbeSucceeds() {
        DependencyHealthReport report = new DependencyHealthService(List.of(
                new StubProbe("postgresql", DependencyStatus.up("SELECT 1; vector 0.8.6")),
                new StubProbe("redis", DependencyStatus.up("PONG"))))
                .check();

        assertThat(report.status()).isEqualTo("UP");
        assertThat(report.components()).allSatisfy((name, status) -> assertThat(status.status()).isEqualTo("UP"));
    }

    private record StubProbe(String name, DependencyStatus result) implements DependencyProbe {

        @Override
        public DependencyStatus check() {
            return result;
        }
    }
}
