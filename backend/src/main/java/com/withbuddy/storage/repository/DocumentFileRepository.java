package com.withbuddy.storage.repository;

import com.withbuddy.storage.entity.DocumentFile;
import com.withbuddy.storage.entity.BackupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentFileRepository extends JpaRepository<DocumentFile, Long> {

    Optional<DocumentFile> findByDocumentId(Long documentId);

    List<DocumentFile> findByDocumentIdIn(Collection<Long> documentIds);

    List<DocumentFile> findByBackupStatusInAndDeletedAtIsNull(List<BackupStatus> statuses, Pageable pageable);
}
