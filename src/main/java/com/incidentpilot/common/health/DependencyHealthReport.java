package com.incidentpilot.common.health;

import java.util.Map;

public record DependencyHealthReport(String status, Map<String, DependencyStatus> components) {
}
