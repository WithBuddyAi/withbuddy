package com.withbuddy.storage.repository;

import com.withbuddy.storage.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query("""
            select d
            from Document d
            where d.isActive = true
              and (d.companyCode = :companyCode or d.companyCode is null)
              and (:documentType is null or d.documentType = :documentType)
              and (:search is null or lower(d.title) like lower(concat('%', :search, '%')))
            """)
    Page<Document> searchDocuments(
            @Param("companyCode") String companyCode,
            @Param("documentType") String documentType,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
            select d
            from Document d
            where d.isActive = true
              and (:documentType is null or d.documentType = :documentType)
              and (:search is null or lower(d.title) like lower(concat('%', :search, '%')))
            """)
    Page<Document> searchDocumentsForAdmin(
            @Param("documentType") String documentType,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
            select d.id
            from Document d
            where d.isActive = true
              and (d.companyCode = :companyCode or d.companyCode is null)
            order by d.id asc
            """)
    List<Long> findActiveDocumentIdsByCompanyCode(@Param("companyCode") String companyCode);

    @Query("""
            select d.id
            from Document d
            where d.isActive = true
            order by d.id asc
            """)
    List<Long> findAllActiveDocumentIds();

    Optional<Document> findByIdAndIsActiveTrue(Long id);

    List<Document> findByIdInAndIsActiveTrue(Collection<Long> ids);
}
