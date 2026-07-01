package com.withbuddy.buddy.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "unanswered_question_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnansweredQuestionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "company_code", nullable = false, length = 20)
    private String companyCode;

    @Column(name = "question_message_id", nullable = false)
    private Long questionMessageId;

    @Column(name = "answer_message_id", nullable = false)
    private Long answerMessageId;

    @Column(name = "question_content", nullable = false, columnDefinition = "TEXT")
    private String questionContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_type", nullable = false, length = 30)
    private MessageType answerType;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @Column(name = "embedding_dimension")
    private Integer embeddingDimension;

    @Column(name = "embedding_vector", columnDefinition = "JSON")
    private String embeddingVector;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public UnansweredQuestionLog(
            Long userId,
            String companyCode,
            Long questionMessageId,
            Long answerMessageId,
            String questionContent,
            MessageType answerType,
            Long latencyMs,
            String embeddingModel,
            Integer embeddingDimension,
            String embeddingVector
    ) {
        this.userId = userId;
        this.companyCode = companyCode;
        this.questionMessageId = questionMessageId;
        this.answerMessageId = answerMessageId;
        this.questionContent = questionContent;
        this.answerType = answerType;
        this.latencyMs = latencyMs;
        this.embeddingModel = embeddingModel;
        this.embeddingDimension = embeddingDimension;
        this.embeddingVector = embeddingVector;
    }
}
