package com.incidentpilot.answer;

import com.incidentpilot.common.reliability.RequestGuard;
import com.incidentpilot.retrieval.RetrievalResult;
import com.incidentpilot.retrieval.RetrievalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("models")
@RequestMapping("/api/v1")
public class DiagnosisController {
    private final RagService rag;
    private final RetrievalService retrieval;
    private final RequestGuard guard;

    public DiagnosisController(RagService rag, RetrievalService retrieval, RequestGuard guard) {
        this.rag = rag;
        this.retrieval = retrieval;
        this.guard = guard;
    }

    @PostMapping("/diagnoses")
    public RagService.Diagnosis diagnose(@Valid @RequestBody Query request, HttpServletRequest http) {
        try (var lease = guard.acquire(RequestGuard.clientKey(http))) {
            return rag.diagnose(request.query(), request.topK(), request.mode());
        }
    }

    /** 纯检索不调用模型，只受参数校验约束，便于在限流之外做检索对照。 */
    @PostMapping("/retrieval/search")
    public RetrievalResult search(@Valid @RequestBody Query request) {
        return retrieval.search(request.query(), request.topK(), request.mode());
    }

    public record Query(@NotBlank @Size(max = 2000) String query, @Min(1) @Max(10) int topK,
                        @Pattern(regexp = "dense|lexical|hybrid|hybrid-rerank") String mode) {
        public Query { if (mode == null) mode = "dense"; }
    }
}
