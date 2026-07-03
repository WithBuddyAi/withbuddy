package com.withbuddy.buddy.chat.repository;

import com.withbuddy.buddy.chat.entity.UnansweredQuestionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface UnansweredQuestionLogRepository extends JpaRepository<UnansweredQuestionLog, Long> {

    @Query(value = """
            SELECT DISTINCT log.company_code
            FROM unanswered_question_logs log
            WHERE DATE(CONVERT_TZ(log.created_at, '+00:00', '+09:00')) >= :windowStartDate
              AND DATE(CONVERT_TZ(log.created_at, '+00:00', '+09:00')) <= :analysisDate
              AND log.answer_type = 'no_result'
            ORDER BY log.company_code
            """, nativeQuery = true)
    List<String> findNoResultPatternCompanyCodes(
            @Param("windowStartDate") LocalDate windowStartDate,
            @Param("analysisDate") LocalDate analysisDate
    );

    @Query(value = """
            SELECT
                log.id AS logId,
                log.question_content AS questionContent
            FROM unanswered_question_logs log
            WHERE DATE(CONVERT_TZ(log.created_at, '+00:00', '+09:00')) >= :windowStartDate
              AND DATE(CONVERT_TZ(log.created_at, '+00:00', '+09:00')) <= :analysisDate
              AND log.answer_type = 'no_result'
              AND log.company_code = :companyCode
            ORDER BY log.created_at ASC, log.id ASC
            """, nativeQuery = true)
    List<NoResultPatternSourceProjection> findNoResultPatternSources(
            @Param("companyCode") String companyCode,
            @Param("windowStartDate") LocalDate windowStartDate,
            @Param("analysisDate") LocalDate analysisDate
    );

    interface NoResultPatternSourceProjection {
        Long getLogId();
        String getQuestionContent();
    }
}
