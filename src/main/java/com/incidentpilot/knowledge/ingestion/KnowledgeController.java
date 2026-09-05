package com.incidentpilot.knowledge.ingestion;

import java.util.*;
import com.incidentpilot.knowledge.embedding.DocumentEmbeddingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("models")
@RequestMapping("/api/v1/knowledge/documents")
public class KnowledgeController {
    private final DocumentIngestionService ingestion;
    private final DocumentEmbeddingService indexing;
    public KnowledgeController(DocumentIngestionService ingestion, DocumentEmbeddingService indexing) {
        this.ingestion = ingestion; this.indexing = indexing;
    }
    @PostMapping
    public DocumentEmbeddingService.IndexResult ingest(@Valid @RequestBody DocumentRequest request) {
        var chunks = request.chunks().stream().map(c -> new ChunkInput(c.chunkIndex(), c.content(), c.metadata())).toList();
        var stored = ingestion.ingest(new DocumentIngestionRequest(request.sourceKey(), request.title(),
                request.documentType(), request.serviceName(), request.sourceUri(), chunks));
        return indexing.index(stored.documentId());
    }
    public record DocumentRequest(@NotBlank @Size(max=200) String sourceKey, @NotBlank @Size(max=500) String title,
            @NotBlank @Size(max=100) String documentType, @Size(max=100) String serviceName,
            @NotBlank @Size(max=1000) String sourceUri, @NotEmpty @Size(max=100) List<@Valid ChunkRequest> chunks) { }
    public record ChunkRequest(@Min(0) int chunkIndex, @NotBlank @Size(max=4000) String content,
                               @NotNull Map<String,Object> metadata) { }
}
