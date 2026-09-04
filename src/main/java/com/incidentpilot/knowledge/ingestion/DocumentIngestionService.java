package com.incidentpilot.knowledge.ingestion;

import com.incidentpilot.knowledge.persistence.DocumentChunkEntity;
import com.incidentpilot.knowledge.persistence.DocumentChunkRepository;
import com.incidentpilot.knowledge.persistence.DocumentEntity;
import com.incidentpilot.knowledge.persistence.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class DocumentIngestionService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentContentHasher contentHasher;
    private final Clock clock;

    public DocumentIngestionService(
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            DocumentContentHasher contentHasher,
            Clock clock
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.contentHasher = contentHasher;
        this.clock = clock;
    }

    @Transactional
    public DocumentIngestionResult ingest(DocumentIngestionRequest request) {
        String contentHash = contentHasher.sha256(request.chunks());
        Instant now = clock.instant();

        return documentRepository.findBySourceKey(request.sourceKey())
                .map(document -> updateExisting(document, request, contentHash, now))
                .orElseGet(() -> createNew(request, contentHash, now));
    }

    private DocumentIngestionResult createNew(
            DocumentIngestionRequest request,
            String contentHash,
            Instant now
    ) {
        DocumentEntity document = DocumentEntity.create(
                request.sourceKey(),
                request.title(),
                request.documentType(),
                request.serviceName(),
                request.sourceUri(),
                contentHash,
                now
        );
        documentRepository.save(document);
        chunkRepository.saveAll(toChunkEntities(document, request.chunks(), now));

        return result(document, request, contentHash, DocumentIngestionStatus.CREATED);
    }

    private DocumentIngestionResult updateExisting(
            DocumentEntity document,
            DocumentIngestionRequest request,
            String contentHash,
            Instant now
    ) {
        boolean contentUnchanged = document.getContentHash().equals(contentHash);

        document.refresh(
                request.title(),
                request.documentType(),
                request.serviceName(),
                request.sourceUri(),
                contentHash,
                now
        );

        if (contentUnchanged) {
            return result(
                    document,
                    request,
                    contentHash,
                    DocumentIngestionStatus.CONTENT_UNCHANGED
            );
        }

        chunkRepository.deleteAllByDocumentId(document.getId());
        chunkRepository.saveAll(toChunkEntities(document, request.chunks(), now));

        return result(
                document,
                request,
                contentHash,
                DocumentIngestionStatus.CONTENT_REPLACED
        );
    }

    private List<DocumentChunkEntity> toChunkEntities(
            DocumentEntity document,
            List<ChunkInput> chunks,
            Instant now
    ) {
        return chunks.stream()
                .map(chunk -> DocumentChunkEntity.create(
                        document,
                        chunk.chunkIndex(),
                        chunk.content(),
                        chunk.metadata(),
                        now
                ))
                .toList();
    }

    private DocumentIngestionResult result(
            DocumentEntity document,
            DocumentIngestionRequest request,
            String contentHash,
            DocumentIngestionStatus status
    ) {
        return new DocumentIngestionResult(
                document.getId(),
                contentHash,
                request.chunks().size(),
                status
        );
    }
}
