package com.incidentpilot.knowledge.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, UUID> {

    List<DocumentChunkEntity> findAllByDocument_IdOrderByChunkIndexAsc(UUID documentId);

    @Modifying(flushAutomatically = true) //  这不是 SELECT，而是一条修改数据库的语句。
    @Query("delete from DocumentChunkEntity chunk where chunk.document.id = :documentId")
    int deleteAllByDocumentId(@Param("documentId") UUID documentId);
}
