package com.incidentpilot.knowledge.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "document_chunk")
public class DocumentChunkEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_chunk_id")
    private DocumentChunkEntity parentChunk;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false)
    private String content;

    @JdbcTypeCode(SqlTypes.JSON) //  不属于通用 JPA 标准，而是 Hibernate 额外提供的能力。
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean newEntity = true;

    protected DocumentChunkEntity() {
    }

    public static DocumentChunkEntity create(
            DocumentEntity document,
            int chunkIndex,
            String content,
            Map<String, Object> metadata,
            Instant now
    ) {
        DocumentChunkEntity entity = new DocumentChunkEntity();
        entity.id = UUID.randomUUID();
        entity.document = document;
        entity.chunkIndex = chunkIndex;
        entity.content = content;
        entity.metadata = new LinkedHashMap<>(metadata);
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        newEntity = false;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public Map<String, Object> getMetadata() {
        return Map.copyOf(metadata);
    }
}
