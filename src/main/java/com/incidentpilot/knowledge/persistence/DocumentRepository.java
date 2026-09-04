package com.incidentpilot.knowledge.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    Optional<DocumentEntity> findBySourceKey(String sourceKey);
}
