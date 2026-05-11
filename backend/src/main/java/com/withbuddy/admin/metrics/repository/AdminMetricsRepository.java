package com.withbuddy.admin.metrics.repository;

import com.withbuddy.account.user.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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
               AND u.hire_date <= DATE_SUB(:asOfDate, INTERVAL 6 DAY)
            LEFT JOIN chat_messages rag_message
                ON rag_message.user_id = u.id
               AND rag_message.sender_type = 'BOT'
               AND rag_message.message_type = 'rag_answer'
               AND rag_message.created_at >= u.hire_date
               AND rag_message.created_at < DATE_ADD(u.hire_date, INTERVAL 7 DAY)
               AND rag_message.created_at < :dayAfter
            WHERE (:companyCode IS NULL OR c.company_code = :companyCode)
            GROUP BY c.company_code, c.name
            ORDER BY c.company_code
            """, nativeQuery = true)
    List<RagExperienceMetricProjection> findRagExperienceRateMetrics(
            @Param("companyCode") String companyCode,
            @Param("asOfDate") LocalDate asOfDate,
            @Param("dayAfter") LocalDate dayAfter
    );

    @Query(value = """
            SELECT
                c.company_code AS companyCode,
                c.name AS companyName,
                COUNT(DISTINCT u.id) AS targetUsers,
                COUNT(DISTINCT CASE
                    WHEN d0_click.id IS NOT NULL OR d0_question.id IS NOT NULL THEN u.id
                END) AS firstInteractionUsers
            FROM companies c
            LEFT JOIN users u
                ON u.company_code = c.company_code
               AND u.role = 'USER'
               AND u.hire_date <= :asOfDate
            LEFT JOIN user_activity_logs d0_click
                ON d0_click.user_id = u.id
               AND d0_click.event_type = 'BUTTON_CLICK'
               AND d0_click.created_at >= u.hire_date
               AND d0_click.created_at < DATE_ADD(u.hire_date, INTERVAL 1 DAY)
               AND d0_click.created_at < :dayAfter
            LEFT JOIN chat_messages d0_question
                ON d0_question.user_id = u.id
               AND d0_question.sender_type = 'USER'
               AND d0_question.message_type = 'user_question'
               AND d0_question.created_at >= u.hire_date
               AND d0_question.created_at < DATE_ADD(u.hire_date, INTERVAL 1 DAY)
               AND d0_question.created_at < :dayAfter
            WHERE (:companyCode IS NULL OR c.company_code = :companyCode)
            GROUP BY c.company_code, c.name
            ORDER BY c.company_code
            """, nativeQuery = true)
    List<FirstInteractionMetricProjection> findFirstInteractionRateMetrics(
            @Param("companyCode") String companyCode,
            @Param("asOfDate") LocalDate asOfDate,
            @Param("dayAfter") LocalDate dayAfter
    );

    @Query(value = """
            SELECT
                c.company_code AS companyCode,
                c.name AS companyName,
                COUNT(DISTINCT CASE WHEN d0_chat.id IS NOT NULL THEN u.id END) AS d0Users,
                COUNT(DISTINCT CASE
                    WHEN d0_chat.id IS NOT NULL AND revisit_chat.id IS NOT NULL THEN u.id
                END) AS revisitUsers
            FROM companies c
            LEFT JOIN users u
                ON u.company_code = c.company_code
               AND u.role = 'USER'
               AND u.hire_date <= DATE_SUB(:asOfDate, INTERVAL 6 DAY)
            LEFT JOIN user_activity_logs d0_chat
                ON d0_chat.user_id = u.id
               AND d0_chat.event_type = 'SESSION_START'
               AND d0_chat.event_target = 'CHAT'
               AND d0_chat.created_at >= u.hire_date
               AND d0_chat.created_at < DATE_ADD(u.hire_date, INTERVAL 1 DAY)
               AND d0_chat.created_at < :dayAfter
            LEFT JOIN user_activity_logs revisit_chat
                ON revisit_chat.user_id = u.id
               AND revisit_chat.event_type = 'SESSION_START'
               AND revisit_chat.event_target = 'CHAT'
               AND revisit_chat.created_at >= DATE_ADD(u.hire_date, INTERVAL 1 DAY)
               AND revisit_chat.created_at < DATE_ADD(u.hire_date, INTERVAL 7 DAY)
               AND revisit_chat.created_at < :dayAfter
            WHERE (:companyCode IS NULL OR c.company_code = :companyCode)
            GROUP BY c.company_code, c.name
            ORDER BY c.company_code
            """, nativeQuery = true)
    List<RevisitMetricProjection> findRevisitRateMetrics(
            @Param("companyCode") String companyCode,
            @Param("asOfDate") LocalDate asOfDate,
            @Param("dayAfter") LocalDate dayAfter
    );

    @Query(value = """
            SELECT
                c.company_code AS companyCode,
                c.name AS companyName,
                COUNT(ai_message.id) AS totalAiAnswers,
                COUNT(CASE
                    WHEN ai_message.message_type IN ('no_result', 'out_of_scope') THEN 1
                END) AS unansweredAnswers
            FROM companies c
            LEFT JOIN users u
                ON u.company_code = c.company_code
               AND u.role = 'USER'
               AND u.hire_date <= :asOfDate
            LEFT JOIN chat_messages ai_message
                ON ai_message.user_id = u.id
               AND ai_message.sender_type = 'BOT'
               AND ai_message.message_type IN ('rag_answer', 'no_result', 'out_of_scope')
               AND ai_message.created_at < :dayAfter
            WHERE (:companyCode IS NULL OR c.company_code = :companyCode)
            GROUP BY c.company_code, c.name
            ORDER BY c.company_code
            """, nativeQuery = true)
    List<UnansweredMetricProjection> findUnansweredRateMetrics(
            @Param("companyCode") String companyCode,
            @Param("asOfDate") LocalDate asOfDate,
            @Param("dayAfter") LocalDate dayAfter
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
               AND u.hire_date <= :asOfDate
            LEFT JOIN (
                SELECT
                    log.user_id AS user_id,
                    MIN(log.created_at) AS first_login_at
                FROM user_activity_logs log
                WHERE log.event_type = 'SESSION_START'
                  AND log.event_target = 'LOGIN'
                  AND log.created_at < :dayAfter
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
                  AND message.created_at < :dayAfter
                GROUP BY message.user_id
            ) rag_stats
                ON rag_stats.user_id = u.id
            WHERE (:companyCode IS NULL OR c.company_code = :companyCode)
            GROUP BY c.company_code, c.name
            ORDER BY c.company_code
            """, nativeQuery = true)
    List<TtaMetricProjection> findTtaMetrics(
            @Param("companyCode") String companyCode,
            @Param("asOfDate") LocalDate asOfDate,
            @Param("dayAfter") LocalDate dayAfter
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
        Long getUnansweredAnswers();
    }

    interface TtaMetricProjection {
        String getCompanyCode();
        String getCompanyName();
        Long getLoggedInUsers();
        Long getMeasuredUsers();
        Double getAverageTtaMinutes();
    }
}
