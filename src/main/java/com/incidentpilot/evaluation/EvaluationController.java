package com.incidentpilot.evaluation;

import java.io.IOException;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("models")
@RequestMapping("/api/v1/evaluations/runs")
public class EvaluationController {
    private final EvaluationService evaluation;
    private final BehaviorEvaluationService behavior;

    public EvaluationController(EvaluationService evaluation, BehaviorEvaluationService behavior) {
        this.evaluation = evaluation;
        this.behavior = behavior;
    }

    /** 检索对照评测：不调用模型，可以频繁重跑。 */
    @PostMapping
    public EvaluationService.Run run() throws IOException { return evaluation.run(); }

    @GetMapping("/{id}")
    public EvaluationService.Run read(@PathVariable UUID id) throws IOException { return evaluation.read(id); }

    /** 行为评测：无答案拒绝、提示注入抵抗、Agent 路由与引用。每个 case 都会真实调用模型。 */
    @PostMapping("/behavior")
    public BehaviorEvaluationService.Run behaviorRun() throws IOException { return behavior.run(); }

    @GetMapping("/behavior/{id}")
    public BehaviorEvaluationService.Run readBehavior(@PathVariable UUID id) throws IOException {
        return behavior.read(id);
    }
}
