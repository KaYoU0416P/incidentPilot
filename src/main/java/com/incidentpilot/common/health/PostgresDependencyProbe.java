package com.incidentpilot.common.health;

import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class PostgresDependencyProbe implements DependencyProbe {

    private final JdbcTemplate jdbcTemplate;

    PostgresDependencyProbe(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String name() {
        return "postgresql";
    }

    @Override
    public DependencyStatus check() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            String vectorVersion = jdbcTemplate.queryForObject(
                    "SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class);

            return Objects.equals(result, 1) && vectorVersion != null
                    ? DependencyStatus.up("SELECT 1; vector " + vectorVersion)
                    : new DependencyStatus("DOWN", "unexpected probe result");
        } catch (RuntimeException exception) {
            return DependencyStatus.down(exception);
        }
    }
}
