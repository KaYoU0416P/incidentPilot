package com.incidentpilot.agent;

import java.util.UUID;
import com.incidentpilot.common.reliability.RequestGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("models")
@RequestMapping("/api/v1/agent")
public class AgentController {
    private final BoundedAgentService agent;
    private final AgentRunRecorder runs;
    private final RequestGuard guard;

    public AgentController(BoundedAgentService agent, AgentRunRecorder runs, RequestGuard guard) {
        this.agent = agent;
        this.runs = runs;
        this.guard = guard;
    }

    @PostMapping("/diagnoses")
    public BoundedAgentService.AgentDiagnosis diagnose(@Valid @RequestBody Request request, HttpServletRequest http) {
        try (var lease = guard.acquire(RequestGuard.clientKey(http))) {
            return agent.diagnose(request.query());
        }
    }

    /** 读取 Redis 中带 TTL 的运行状态；过期或未记录返回 404。 */
    @GetMapping("/runs/{runId}")
    public ResponseEntity<AgentRunRecorder.RunSnapshot> run(@PathVariable UUID runId) {
        return runs.find(runId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record Request(@NotBlank @Size(max = 2000) String query) { }
}
