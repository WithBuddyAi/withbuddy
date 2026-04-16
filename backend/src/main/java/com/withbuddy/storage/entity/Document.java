package com.withbuddy.storage.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_code", length = 20)
    private String companyCode;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "file_path", length = 500)
    private String content;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    @Column(name = "department", nullable = false, length = 50)
    private String department;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Document(
            String companyCode,
            String title,
            String content,
            String documentType,
            String department,
            boolean isActive
    ) {
        this.companyCode = companyCode;
        this.title = title;
        this.content = content;
        this.documentType = documentType;
        this.department = department;
        this.isActive = isActive;
    }

    public void softDelete() {
        this.isActive = false;
    }
}
