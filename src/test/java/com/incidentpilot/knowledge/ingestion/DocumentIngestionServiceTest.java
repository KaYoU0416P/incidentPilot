package com.incidentpilot.knowledge.ingestion;

import com.incidentpilot.knowledge.persistence.DocumentChunkEntity;
import com.incidentpilot.knowledge.persistence.DocumentChunkRepository;
import com.incidentpilot.knowledge.persistence.DocumentEntity;
import com.incidentpilot.knowledge.persistence.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T04:30:00Z");

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentChunkRepository chunkRepository;

    private DocumentContentHasher contentHasher;
    private DocumentIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        contentHasher = new DocumentContentHasher();
        ingestionService = new DocumentIngestionService(
                documentRepository,
                chunkRepository,
                contentHasher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsDocumentAndPersistsChunksInIndexOrder() {
        DocumentIngestionRequest request = requestWithChunks(
                chunk(1, "Restart the worker."),
                chunk(0, "Check connection pool usage.")
        );
        when(documentRepository.findBySourceKey(request.sourceKey())).thenReturn(Optional.empty());

        DocumentIngestionResult result = ingestionService.ingest(request);

        assertThat(result.status()).isEqualTo(DocumentIngestionStatus.CREATED);
        assertThat(result.contentHash()).hasSize(64);
        assertThat(result.chunkCount()).isEqualTo(2);

        verify(documentRepository).save(org.mockito.ArgumentMatchers.any(DocumentEntity.class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentChunkEntity>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(chunksCaptor.capture());
        assertThat(chunksCaptor.getValue())
                .extracting(DocumentChunkEntity::getChunkIndex)
                .containsExactly(0, 1);
    }

    @Test
    void doesNotRewriteChunksWhenContentHashMatches() {
        DocumentIngestionRequest request = requestWithChunks(chunk(0, "Check timeout metrics."));
        String contentHash = contentHasher.sha256(request.chunks());
        DocumentEntity existing = existingDocument(contentHash);
        when(documentRepository.findBySourceKey(request.sourceKey()))
                .thenReturn(Optional.of(existing));

        DocumentIngestionResult result = ingestionService.ingest(request);

        assertThat(result.documentId()).isEqualTo(existing.getId());
        assertThat(result.status()).isEqualTo(DocumentIngestionStatus.CONTENT_UNCHANGED);
        verify(chunkRepository, never()).deleteAllByDocumentId(existing.getId());
        verify(chunkRepository, never()).saveAll(anyList());
    }

    @Test
    void replacesChunksWhenContentHashChanges() {
        DocumentIngestionRequest request = requestWithChunks(chunk(0, "New runbook content."));
        DocumentEntity existing = existingDocument("0".repeat(64));
        when(documentRepository.findBySourceKey(request.sourceKey()))
                .thenReturn(Optional.of(existing));

        DocumentIngestionResult result = ingestionService.ingest(request);

        assertThat(result.status()).isEqualTo(DocumentIngestionStatus.CONTENT_REPLACED);
        verify(chunkRepository).deleteAllByDocumentId(existing.getId());
        verify(chunkRepository).saveAll(anyList());
    }

    @Test
    void rejectsDuplicateChunkIndexes() {
        assertThatIllegalArgumentException().isThrownBy(() -> requestWithChunks(
                chunk(0, "first"),
                chunk(0, "second")
        ));
    }

    private DocumentEntity existingDocument(String contentHash) {
        return DocumentEntity.create(
                "runbook:payments:timeout",
                "Payment timeout",
                "runbook",
                "payment-service",
                "docs/runbooks/payment-timeout.md",
                contentHash,
                NOW.minusSeconds(60)
        );
    }

    private DocumentIngestionRequest requestWithChunks(ChunkInput... chunks) {
        return new DocumentIngestionRequest(
                "runbook:payments:timeout",
                " Payment timeout ",
                "runbook",
                "payment-service",
                "docs/runbooks/payment-timeout.md",
                List.of(chunks)
        );
    }

    private ChunkInput chunk(int index, String content) {
        return new ChunkInput(index, content, Map.of("section", "diagnosis"));
    }
}
