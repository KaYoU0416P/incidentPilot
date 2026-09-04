package com.incidentpilot.common.health;

import static java.util.Comparator.comparing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DependencyHealthService {

    private final List<DependencyProbe> probes;

    DependencyHealthService(List<DependencyProbe> probes) {
        this.probes = List.copyOf(probes);
    }

    public DependencyHealthReport check() {
        Map<String, DependencyStatus> components = new LinkedHashMap<>();
        probes.stream()
                .sorted(comparing(DependencyProbe::name))
                .forEach(probe -> components.put(probe.name(), probe.check()));

        boolean allUp = components.values().stream().allMatch(component -> "UP".equals(component.status()));
        return new DependencyHealthReport(allUp ? "UP" : "DEGRADED", Map.copyOf(components));
    }
}
