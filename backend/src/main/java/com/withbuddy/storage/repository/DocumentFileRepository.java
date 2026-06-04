package com.withbuddy.storage.repository;

import com.withbuddy.storage.entity.DocumentFile;
import com.withbuddy.storage.entity.BackupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentFileRepository extends JpaRepository<DocumentFile, Long> {

    Optional<DocumentFile> findByDocumentId(Long documentId);

    List<DocumentFile> findByDocumentIdIn(Collection<Long> documentIds);

    List<DocumentFile> findByBackupStatusInAndDeletedAtIsNull(List<BackupStatus> statuses, Pageable pageable);

    @Query("""
            select coalesce(sum(df.fileSize), 0)
            from DocumentFile df
            where df.deletedAt is null
              and df.companyCode = :companyCode
              and df.documentId in (
                  select d.id
                  from Document d
                  where d.isActive = true
                    and d.companyCode = :companyCode
              )
            """)
    long sumActiveFileSizeByCompanyCode(@Param("companyCode") String companyCode);
}
