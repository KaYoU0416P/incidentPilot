package com.incidentpilot.knowledge.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document")
public class DocumentEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "source_key", nullable = false, unique = true)
    private String sourceKey;

    @Column(nullable = false)
    private String title;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "source_uri", nullable = false)
    private String sourceUri;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean newEntity = true;

    protected DocumentEntity() {
    }

    public static DocumentEntity create(
            String sourceKey,
            String title,
            String documentType,
            String serviceName,
            String sourceUri,
            String contentHash,
            Instant now
    ) {
        DocumentEntity entity = new DocumentEntity();
        entity.id = UUID.randomUUID();
        entity.sourceKey = sourceKey;
        entity.title = title;
        entity.documentType = documentType;
        entity.serviceName = serviceName;
        entity.sourceUri = sourceUri;
        entity.contentHash = contentHash;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void refresh(
            String title,
            String documentType,
            String serviceName,
            String sourceUri,
            String contentHash,
            Instant now
    ) {
        this.title = title;
        this.documentType = documentType;
        this.serviceName = serviceName;
        this.sourceUri = sourceUri;
        this.contentHash = contentHash;
        this.updatedAt = now;
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

    public String getSourceKey() {
        return sourceKey;
    }

    public String getContentHash() {
        return contentHash;
    }
}
