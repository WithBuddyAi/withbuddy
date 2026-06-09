package com.withbuddy.admin.metrics.repository;

import com.withbuddy.account.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@org.springframework.stereotype.Repository
public interface AdminMetricsRepository extends Repository<User, Long> {

    @Query(value = """
            SELECT
                c.company_code AS companyCode,
                c.name AS companyName,
                COUNT(DISTINCT u.id) AS targetUsers,
                COUNT(DISTINCT CASE WHEN rag_message.id IS NOT NULL THEN u.id END) AS ragReceivedUsers
            FROM companies c
            LEFT JOIN users u
                ON u.company_code = c.company_code
               AND u.role = 'USER'
               AND u.account_status IN ('ACTIVE', 'READ_ONLY')
               AND u.hire_date >= :cohortStartDate
               AND u.hire_date <= DATE_SUB(:asOfDate, INTERVAL 6 DAY)
            LEFT JOIN chat_messages rag_message
                ON rag_message.user_id = u.id
               AND rag_message.sender_type = 'BOT'
               AND rag_message.message_type = 'rag_answer'
               AND DATE(CONVERT_TZ(rag_message.created_at, '+00:00', '+09:00')) >= u.hire_date
               AND DATE(CONVERT_TZ(rag_message.created_at, '+00:00', '+09:00')) < DATE_ADD(u.hire_date, INTERVAL 7 DAY)
               AND DATE(CONVERT_TZ(rag_message.created_at, '+00:00', '+09:00')) <= :asOfDate
            WHERE (:companyCode IS NULL OR c.company_code = :companyCode)
            GROUP BY c.company_code, c.name
            ORDER BY c.company_code
            """, nativeQuery = true)
    List<RagExperienceMetricProjection> findRagExperienceRateMetrics(
            @Param("companyCode") String companyCode,
            @Param("cohortStartDate") LocalDate cohortStartDate,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query(value = """
            SELECT
                c.company_code AS companyCode,
                c.name AS companyName,
                COUNT(DISTINCT u.id) AS targetUsers,
                COUNT(DISTINCT CASE WHEN d0_session.id IS NOT NULL THEN u.id END) AS firstInteractionUsers
            FROM companies c
            LEFT JOIN users u
                ON u.company_code = c.company_code
               AND u.role = 'USER'
               AND u.account_status IN ('ACTIVE', 'READ_ONLY')
               AND u.hire_date >= :cohortStartDate
               AND u.hire_date <= :asOfDate
            LEFT JOIN user_activity_logs d0_session
                ON d0_session.user_id = u.id
               AND d0_session.event_type = 'SESSION_START'
               AND DATE(CONVERT_TZ(d0_session.created_at, '+00:00', '+09:00')) = u.hire_date
            WHERE (:companyCode IS NULL OR c.company_code = :companyCode)
            GROUP BY c.company_code, c.name
            ORDER BY c.company_code
            """, nativeQuery = true)
    List<FirstInteractionMetricProjection> findFirstInteractionRateMetrics(
            @Param("companyCode") String companyCode,
            @Param("cohortStartDate") LocalDate cohortStartDate,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query(value = """
            SELECT
                c.company_code AS companyCode,
                c.name AS companyName,
                COUNT(DISTINCT CASE WHEN d0_session.id IS NOT NULL THEN u.id END) AS d0Users,
                COUNT(DISTINCT CASE
                    WHEN d0_session.id IS NOT NULL AND revisit_session.id IS NOT NULL THEN u.id
                END) AS revisitUsers
            FROM companies c
            LEFT JOIN users u
                ON u.company_code = c.company_code
               AND u.role = 'USER'
               AND u.account_status IN ('ACTIVE', 'READ_ONLY')
               AND u.hire_date >= :cohortStartDate
               AND u.hire_date <= DATE_SUB(:asOfDate, INTERVAL 6 DAY)
            LEFT JOIN user_activity_logs d0_session
                ON d0_session.user_id = u.id
               AND d0_session.event_type = 'SESSION_START'
               AND DATE(CONVERT_TZ(d0_session.created_at, '+00:00', '+09:00')) = u.hire_date
            LEFT JOIN user_activity_logs revisit_session
                ON revisit_session.user_id = u.id
               AND revisit_session.event_type = 'SESSION_START'
               AND DATE(CONVERT_TZ(revisit_session.created_at, '+00:00', '+09:00')) >= DATE_ADD(u.hire_date, INTERVAL 1 DAY)
               AND DATE(CONVERT_TZ(revisit_session.created_at, '+00:00', '+09:00')) < DATE_ADD(u.hire_date, INTERVAL 7 DAY)
               AND DATE(CONVERT_TZ(revisit_session.created_at, '+00:00', '+09:00')) <= :asOfDate
            WHERE (:companyCode IS NULL OR c.company_code = :companyCode)
            GROUP BY c.company_code, c.name
            ORDER BY c.company_code
            """, nativeQuery = true)
    List<RevisitMetricProjection> findRevisitRateMetrics(
            @Param("companyCode") String companyCode,
            @Param("cohortStartDate") LocalDate cohortStartDate,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query(value = """
            SELECT
                c.company_code AS companyCode,
                c.name AS companyName,
                COUNT(ai_message.id) AS totalAiAnswers,
                COUNT(CASE WHEN ai_message.message_type = 'no_result' THEN 1 END) AS noResultAnswers,
                COUNT(CASE WHEN ai_message.message_type = 'out_of_scope' THEN 1 END) AS outOfScopeAnswers,
                COUNT(CASE WHEN ai_message.message_type = 'sensitive' THEN 1 END) AS sensitiveAnswers
            FROM companies c
            LEFT JOIN users u
                ON u.company_code = c.company_code
               AND u.role = 'USER'
               AND u.account_status IN ('ACTIVE', 'READ_ONLY')
            LEFT JOIN chat_messages ai_message
                ON ai_message.user_id = u.id
               AND ai_message.sender_type = 'BOT'
               AND ai_message.message_type IN ('rag_answer', 'no_result', 'out_of_scope', 'sensitive')
               AND DATE(CONVERT_TZ(ai_message.created_at, '+00:00', '+09:00')) >= :windowStartDate
               AND DATE(CONVERT_TZ(ai_message.created_at, '+00:00', '+09:00')) <= :asOfDate
            WHERE (:companyCode IS NULL OR c.company_code = :companyCode)
            GROUP BY c.company_code, c.name
            ORDER BY c.company_code
            """, nativeQuery = true)
    List<UnansweredMetricProjection> findUnansweredRateMetrics(
            @Param("companyCode") String companyCode,
            @Param("windowStartDate") LocalDate windowStartDate,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query(value = """
            SELECT
                c.company_code AS companyCode,
                c.name AS companyName,
                COUNT(answerable_message.id) AS answerableBotAnswers,
                COUNT(CASE WHEN answerable_message.message_type = 'no_result' THEN 1 END) AS noResultAnswers
            FROM companies c
            LEFT JOIN users u
                ON u.company_code = c.company_code
               AND u.role = 'USER'
               AND u.account_status IN ('ACTIVE', 'READ_ONLY')
            LEFT JOIN chat_messages answerable_message
                ON answerable_message.user_id = u.id
               AND answerable_message.sender_type = 'BOT'
               AND answerable_message.message_type IN ('rag_answer', 'no_result')
               AND DATE(CONVERT_TZ(answerable_message.created_at, '+00:00', '+09:00')) >= :windowStartDate
               AND DATE(CONVERT_TZ(answerable_message.created_at, '+00:00', '+09:00')) <= :asOfDate
            WHERE (:companyCode IS NULL OR c.company_code = :companyCode)
            GROUP BY c.company_code, c.name
            ORDER BY c.company_code
            """, nativeQuery = true)
    List<DocumentGapMetricProjection> findDocumentGapRateMetrics(
            @Param("companyCode") String companyCode,
            @Param("windowStartDate") LocalDate windowStartDate,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query(value = """
            SELECT
                c.company_code AS companyCode,
                c.name AS companyName,
                COUNT(login_stats.user_id) AS loggedInUsers,
                COUNT(CASE
                    WHEN rag_stats.first_rag_at IS NOT NULL
                     AND rag_stats.first_rag_at >= login_stats.first_login_at THEN 1
                END) AS measuredUsers,
                CASE
                    WHEN COUNT(CASE
                        WHEN rag_stats.first_rag_at IS NOT NULL
                         AND rag_stats.first_rag_at >= login_stats.first_login_at THEN 1
                    END) = 0 THEN NULL
                    ELSE ROUND(AVG(CASE
                        WHEN rag_stats.first_rag_at IS NOT NULL
                         AND rag_stats.first_rag_at >= login_stats.first_login_at
                        THEN TIMESTAMPDIFF(SECOND, login_stats.first_login_at, rag_stats.first_rag_at) / 60.0
                    END), 1)
                END AS averageTtaMinutes
            FROM companies c
            LEFT JOIN users u
                ON u.company_code = c.company_code
               AND u.role = 'USER'
               AND u.account_status IN ('ACTIVE', 'READ_ONLY')
            LEFT JOIN (
                SELECT
                    log.user_id AS user_id,
                    MIN(log.created_at) AS first_login_at
                FROM user_activity_logs log
                WHERE log.event_type = 'SESSION_START'
                  AND log.event_target = 'LOGIN'
                  AND DATE(CONVERT_TZ(log.created_at, '+00:00', '+09:00')) >= :windowStartDate
                  AND DATE(CONVERT_TZ(log.created_at, '+00:00', '+09:00')) <= :asOfDate
                GROUP BY log.user_id
            ) login_stats
                ON login_stats.user_id = u.id
            LEFT JOIN (
                SELECT
                    message.user_id AS user_id,
                    MIN(message.created_at) AS first_rag_at
                FROM chat_messages message
                WHERE message.sender_type = 'BOT'
                  AND message.message_type = 'rag_answer'
                  AND DATE(CONVERT_TZ(message.created_at, '+00:00', '+09:00')) <= :asOfDate
                GROUP BY message.user_id
            ) rag_stats
                ON rag_stats.user_id = u.id
            WHERE (:companyCode IS NULL OR c.company_code = :companyCode)
            GROUP BY c.company_code, c.name
            ORDER BY c.company_code
            """, nativeQuery = true)
    List<TtaMetricProjection> findTtaMetrics(
            @Param("companyCode") String companyCode,
            @Param("windowStartDate") LocalDate windowStartDate,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query(value = """
            SELECT
                c.company_code AS companyCode,
                c.name AS companyName,
                COUNT(login_stats.user_id) AS loggedInUsers,
                COUNT(CASE
                    WHEN rag_stats.first_rag_at IS NULL THEN 1
                END) AS unreachedUsers
            FROM companies c
            LEFT JOIN users u
                ON u.company_code = c.company_code
               AND u.role = 'USER'
               AND u.account_status IN ('ACTIVE', 'READ_ONLY')
            LEFT JOIN (
                SELECT
                    log.user_id AS user_id,
                    MIN(log.created_at) AS first_login_at
                FROM user_activity_logs log
                WHERE log.event_type = 'SESSION_START'
                  AND log.event_target = 'LOGIN'
                  AND DATE(CONVERT_TZ(log.created_at, '+00:00', '+09:00')) >= :windowStartDate
                  AND DATE(CONVERT_TZ(log.created_at, '+00:00', '+09:00')) <= :asOfDate
                GROUP BY log.user_id
            ) login_stats
                ON login_stats.user_id = u.id
            LEFT JOIN (
                SELECT
                    message.user_id AS user_id,
                    MIN(message.created_at) AS first_rag_at
                FROM chat_messages message
                WHERE message.sender_type = 'BOT'
                  AND message.message_type = 'rag_answer'
                  AND DATE(CONVERT_TZ(message.created_at, '+00:00', '+09:00')) <= :asOfDate
                GROUP BY message.user_id
            ) rag_stats
                ON rag_stats.user_id = u.id
            WHERE (:companyCode IS NULL OR c.company_code = :companyCode)
              AND login_stats.user_id IS NOT NULL
            GROUP BY c.company_code, c.name
            ORDER BY c.company_code
            """, nativeQuery = true)
    List<TtaUnreachedMetricProjection> findTtaUnreachedMetrics(
            @Param("companyCode") String companyCode,
            @Param("windowStartDate") LocalDate windowStartDate,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query(value = """
            SELECT
                c.company_code AS companyCode,
                c.name AS companyName,
                COUNT(DISTINCT u.id) AS activeNewUsers,
                COUNT(DISTINCT CASE WHEN question.id IS NULL THEN u.id END) AS unstartedUsers
            FROM companies c
            LEFT JOIN users u
                ON u.company_code = c.company_code
               AND u.role = 'USER'
               AND u.account_status = 'ACTIVE'
               AND u.hire_date <= :asOfDate
            LEFT JOIN chat_messages question
                ON question.user_id = u.id
               AND question.sender_type = 'USER'
               AND question.message_type = 'user_question'
            WHERE (:companyCode IS NULL OR c.company_code = :companyCode)
            GROUP BY c.company_code, c.name
            ORDER BY c.company_code
            """, nativeQuery = true)
    List<UnstartedUsersMetricProjection> findUnstartedUsersMetrics(
            @Param("companyCode") String companyCode,
            @Param("asOfDate") LocalDate asOfDate
    );

    @Query(value = """
            SELECT
                log.company_code AS companyCode,
                log.question_content AS questionContent,
                COUNT(*) AS totalCount,
                COUNT(DISTINCT log.user_id) AS uniqueUsers,
                COUNT(*) AS noResultCount,
                0 AS outOfScopeCount,
                MAX(log.created_at) AS latestOccurredAt
            FROM unanswered_question_logs log
            WHERE DATE(CONVERT_TZ(log.created_at, '+00:00', '+09:00')) >= :windowStartDate
              AND DATE(CONVERT_TZ(log.created_at, '+00:00', '+09:00')) <= :asOfDate
              AND log.answer_type = 'no_result'
              AND (:companyCode IS NULL OR log.company_code = :companyCode)
            GROUP BY log.company_code, log.question_content
            ORDER BY totalCount DESC, uniqueUsers DESC, latestOccurredAt DESC, questionContent
            """,
            countQuery = """
            SELECT COUNT(*) FROM (
                SELECT 1
                FROM unanswered_question_logs log
                WHERE DATE(CONVERT_TZ(log.created_at, '+00:00', '+09:00')) >= :windowStartDate
                  AND DATE(CONVERT_TZ(log.created_at, '+00:00', '+09:00')) <= :asOfDate
                  AND log.answer_type = 'no_result'
                  AND (:companyCode IS NULL OR log.company_code = :companyCode)
                GROUP BY log.company_code, log.question_content
            ) grouped_patterns
            """,
            nativeQuery = true)
    Page<UnansweredQuestionPatternProjection> findUnansweredQuestionPatterns(
            @Param("companyCode") String companyCode,
            @Param("windowStartDate") LocalDate windowStartDate,
            @Param("asOfDate") LocalDate asOfDate,
            Pageable pageable
    );

    interface RagExperienceMetricProjection {
        String getCompanyCode();
        String getCompanyName();
        Long getTargetUsers();
        Long getRagReceivedUsers();
    }

    interface FirstInteractionMetricProjection {
        String getCompanyCode();
        String getCompanyName();
        Long getTargetUsers();
        Long getFirstInteractionUsers();
    }

    interface RevisitMetricProjection {
        String getCompanyCode();
        String getCompanyName();
        Long getD0Users();
        Long getRevisitUsers();
    }

    interface UnansweredMetricProjection {
        String getCompanyCode();
        String getCompanyName();
        Long getTotalAiAnswers();
        Long getNoResultAnswers();
        Long getOutOfScopeAnswers();
        Long getSensitiveAnswers();
    }

    interface DocumentGapMetricProjection {
        String getCompanyCode();
        String getCompanyName();
        Long getAnswerableBotAnswers();
        Long getNoResultAnswers();
    }

    interface TtaMetricProjection {
        String getCompanyCode();
        String getCompanyName();
        Long getLoggedInUsers();
        Long getMeasuredUsers();
        Double getAverageTtaMinutes();
    }

    interface TtaUnreachedMetricProjection {
        String getCompanyCode();
        String getCompanyName();
        Long getLoggedInUsers();
        Long getUnreachedUsers();
    }

    interface UnstartedUsersMetricProjection {
        String getCompanyCode();
        String getCompanyName();
        Long getActiveNewUsers();
        Long getUnstartedUsers();
    }

    interface UnansweredQuestionPatternProjection {
        String getCompanyCode();
        String getQuestionContent();
        Long getTotalCount();
        Long getUniqueUsers();
        Long getNoResultCount();
        Long getOutOfScopeCount();
        LocalDateTime getLatestOccurredAt();
    }
}
