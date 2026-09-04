package com.incidentpilot.api;

import com.incidentpilot.common.health.DependencyHealthReport;
import com.incidentpilot.common.health.DependencyHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
class SystemDependencyController {

    private final DependencyHealthService dependencyHealthService;

    SystemDependencyController(DependencyHealthService dependencyHealthService) {
        this.dependencyHealthService = dependencyHealthService;
    }

    @GetMapping("/dependencies")
    DependencyHealthReport dependencies() {
        return dependencyHealthService.check();
    }
}
