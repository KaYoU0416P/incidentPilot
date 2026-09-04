package com.incidentpilot.common.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("pgvector")
class PgvectorHealthIndicator implements HealthIndicator {

    private final PostgresDependencyProbe probe;

    PgvectorHealthIndicator(PostgresDependencyProbe probe) {
        this.probe = probe;
    }

    @Override
    public Health health() {
        DependencyStatus status = probe.check();
        return "UP".equals(status.status())
                ? Health.up().withDetail("probe", status.detail()).build()
                : Health.down().withDetail("error", status.detail()).build();
    }
}
