package com.withbuddy.admin.metrics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "no_result_question_patterns",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_no_result_question_patterns_company_date",
                        columnNames = {"company_code", "analysis_date"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoResultQuestionPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_code", nullable = false, length = 50)
    private String companyCode;

    @Column(name = "analysis_date", nullable = false)
    private LocalDate analysisDate;

    @Column(name = "top_questions", nullable = false, columnDefinition = "JSON")
    private String topQuestions;

    @Column(name = "ai_status", nullable = false, length = 20)
    private String aiStatus;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "improvement_areas", columnDefinition = "JSON")
    private String improvementAreas;

    @Column(name = "has_sensitive", nullable = false)
    private boolean hasSensitive;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "source_count", nullable = false)
    private int sourceCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public NoResultQuestionPattern(
            String companyCode,
            LocalDate analysisDate,
            String topQuestions,
            String aiStatus,
            String aiSummary,
            String improvementAreas,
            boolean hasSensitive,
            String errorMessage,
            int sourceCount
    ) {
        this.companyCode = companyCode;
        this.analysisDate = analysisDate;
        update(topQuestions, aiStatus, aiSummary, improvementAreas, hasSensitive, errorMessage, sourceCount);
    }

    public void update(
            String topQuestions,
            String aiStatus,
            String aiSummary,
            String improvementAreas,
            boolean hasSensitive,
            String errorMessage,
            int sourceCount
    ) {
        this.topQuestions = topQuestions;
        this.aiStatus = aiStatus;
        this.aiSummary = aiSummary;
        this.improvementAreas = improvementAreas;
        this.hasSensitive = hasSensitive;
        this.errorMessage = errorMessage;
        this.sourceCount = sourceCount;
    }
}
