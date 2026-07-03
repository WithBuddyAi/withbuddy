package com.withbuddy.admin.metrics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.admin.metrics.dto.response.NoResultQuestionPatternRefreshResponse;
import com.withbuddy.admin.metrics.entity.NoResultQuestionPattern;
import com.withbuddy.admin.metrics.repository.NoResultQuestionPatternRepository;
import com.withbuddy.buddy.chat.repository.UnansweredQuestionLogRepository;
import com.withbuddy.infrastructure.ai.client.AiNoResultQuestionClusterClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@Slf4j
public class NoResultQuestionPatternBatchService {

    private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_TOP_N = 5;
    private static final int MAX_TOP_N = 20;

    private final UnansweredQuestionLogRepository unansweredQuestionLogRepository;
    private final NoResultQuestionPatternRepository noResultQuestionPatternRepository;
    private final AiNoResultQuestionClusterClient aiNoResultQuestionClusterClient;
    private final ObjectMapper objectMapper;

    public NoResultQuestionPatternBatchService(
            UnansweredQuestionLogRepository unansweredQuestionLogRepository,
            NoResultQuestionPatternRepository noResultQuestionPatternRepository,
            AiNoResultQuestionClusterClient aiNoResultQuestionClusterClient,
            ObjectMapper objectMapper
    ) {
        this.unansweredQuestionLogRepository = unansweredQuestionLogRepository;
        this.noResultQuestionPatternRepository = noResultQuestionPatternRepository;
        this.aiNoResultQuestionClusterClient = aiNoResultQuestionClusterClient;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${app.metrics.no-result-pattern.cron:0 0 3 * * *}", zone = "Asia/Seoul")
    public void refreshScheduled() {
        LocalDate analysisDate = LocalDate.now(KOREA_ZONE_ID);
        try {
            NoResultQuestionPatternRefreshResponse response = refreshAllCompanies(analysisDate, DEFAULT_TOP_N);
            log.info("no_result 질문 패턴 배치 완료. analysisDate={}, companies={}",
                    analysisDate, response.companies().size());
        } catch (RuntimeException e) {
            log.warn("no_result 질문 패턴 배치 실패. analysisDate={}", analysisDate, e);
        }
    }

    @Transactional
    public NoResultQuestionPatternRefreshResponse refreshAllCompanies(LocalDate analysisDate, int topN) {
        LocalDate resolvedAnalysisDate = resolveAnalysisDate(analysisDate);
        int resolvedTopN = resolveTopN(topN);
        LocalDate windowStartDate = resolvedAnalysisDate.minusDays(6);

        List<String> companyCodes = unansweredQuestionLogRepository.findNoResultPatternCompanyCodes(
                windowStartDate,
                resolvedAnalysisDate
        );

        List<NoResultQuestionPatternRefreshResponse.CompanyResult> results = companyCodes.stream()
                .map(companyCode -> refreshCompanyInternal(companyCode, resolvedAnalysisDate, resolvedTopN))
                .toList();

        return new NoResultQuestionPatternRefreshResponse(resolvedAnalysisDate, resolvedTopN, results);
    }

    @Transactional
    public NoResultQuestionPatternRefreshResponse refreshCompany(String companyCode, LocalDate analysisDate, int topN) {
        LocalDate resolvedAnalysisDate = resolveAnalysisDate(analysisDate);
        int resolvedTopN = resolveTopN(topN);
        NoResultQuestionPatternRefreshResponse.CompanyResult result =
                refreshCompanyInternal(companyCode, resolvedAnalysisDate, resolvedTopN);

        return new NoResultQuestionPatternRefreshResponse(resolvedAnalysisDate, resolvedTopN, List.of(result));
    }

    private NoResultQuestionPatternRefreshResponse.CompanyResult refreshCompanyInternal(
            String companyCode,
            LocalDate analysisDate,
            int topN
    ) {
        LocalDate windowStartDate = analysisDate.minusDays(6);
        List<AiNoResultQuestionClusterClient.ClusterItemRequest> items =
                unansweredQuestionLogRepository.findNoResultPatternSources(companyCode, windowStartDate, analysisDate)
                        .stream()
                        .map(this::toClusterItem)
                        .toList();

        if (items.isEmpty()) {
            NoResultQuestionPattern saved = upsertEmpty(companyCode, analysisDate);
            return new NoResultQuestionPatternRefreshResponse.CompanyResult(
                    companyCode,
                    0,
                    0,
                    "EMPTY",
                    null,
                    saved.getUpdatedAt()
            );
        }

        try {
            AiNoResultQuestionClusterClient.ClusterResponse response =
                    aiNoResultQuestionClusterClient.clusterQuestions(companyCode, analysisDate, topN, items);
            NoResultQuestionPattern saved = upsertPattern(companyCode, analysisDate, response);
            return new NoResultQuestionPatternRefreshResponse.CompanyResult(
                    companyCode,
                    response.sourceCount(),
                    response.topQuestions().size(),
                    response.aiSummary() == null ? "READY" : response.aiSummary().status(),
                    response.aiSummary() == null ? null : response.aiSummary().errorMessage(),
                    saved.getUpdatedAt()
            );
        } catch (RuntimeException e) {
            log.warn("no_result 질문 패턴 회사별 분석 실패. companyCode={}, analysisDate={}",
                    companyCode, analysisDate, e);
            NoResultQuestionPattern saved = upsertFailed(companyCode, analysisDate, items.size(), e.getMessage());
            return new NoResultQuestionPatternRefreshResponse.CompanyResult(
                    companyCode,
                    items.size(),
                    0,
                    "FAILED",
                    e.getMessage(),
                    saved.getUpdatedAt()
            );
        }
    }

    private AiNoResultQuestionClusterClient.ClusterItemRequest toClusterItem(
            UnansweredQuestionLogRepository.NoResultPatternSourceProjection source
    ) {
        return new AiNoResultQuestionClusterClient.ClusterItemRequest(
                source.getLogId(),
                source.getQuestionContent()
        );
    }

    private NoResultQuestionPattern upsertPattern(
            String companyCode,
            LocalDate analysisDate,
            AiNoResultQuestionClusterClient.ClusterResponse response
    ) {
        String topQuestionsJson = writeJson(response.topQuestions());
        String improvementAreasJson = response.aiSummary() == null
                ? "[]"
                : writeJson(response.aiSummary().actions());
        String status = response.aiSummary() == null ? "READY" : response.aiSummary().status();
        String summary = response.aiSummary() == null ? null : response.aiSummary().summary();
        boolean hasSensitive = response.aiSummary() != null && response.aiSummary().hasSensitive();
        String errorMessage = response.aiSummary() == null ? null : response.aiSummary().errorMessage();

        return upsert(
                companyCode,
                analysisDate,
                topQuestionsJson,
                status,
                summary,
                improvementAreasJson,
                hasSensitive,
                errorMessage,
                response.sourceCount()
        );
    }

    private NoResultQuestionPattern upsertEmpty(String companyCode, LocalDate analysisDate) {
        return upsert(companyCode, analysisDate, "[]", "EMPTY", null, "[]", false, null, 0);
    }

    private NoResultQuestionPattern upsertFailed(
            String companyCode,
            LocalDate analysisDate,
            int sourceCount,
            String errorMessage
    ) {
        NoResultQuestionPattern existing = noResultQuestionPatternRepository
                .findByCompanyCodeAndAnalysisDate(companyCode, analysisDate)
                .orElse(null);

        if (existing == null) {
            return upsert(companyCode, analysisDate, "[]", "FAILED", null, "[]", false, errorMessage, sourceCount);
        }

        return upsert(
                companyCode,
                analysisDate,
                existing.getTopQuestions(),
                "FAILED",
                existing.getAiSummary(),
                existing.getImprovementAreas(),
                existing.isHasSensitive(),
                errorMessage,
                sourceCount
        );
    }

    private NoResultQuestionPattern upsert(
            String companyCode,
            LocalDate analysisDate,
            String topQuestionsJson,
            String aiStatus,
            String aiSummary,
            String improvementAreasJson,
            boolean hasSensitive,
            String errorMessage,
            int sourceCount
    ) {
        NoResultQuestionPattern pattern = noResultQuestionPatternRepository
                .findByCompanyCodeAndAnalysisDate(companyCode, analysisDate)
                .orElseGet(() -> new NoResultQuestionPattern(
                        companyCode,
                        analysisDate,
                        topQuestionsJson,
                        aiStatus,
                        aiSummary,
                        improvementAreasJson,
                        hasSensitive,
                        errorMessage,
                        sourceCount
                ));

        pattern.update(topQuestionsJson, aiStatus, aiSummary, improvementAreasJson, hasSensitive, errorMessage, sourceCount);
        return noResultQuestionPatternRepository.saveAndFlush(pattern);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("no_result 질문 패턴 JSON 직렬화에 실패했습니다.", e);
        }
    }

    private LocalDate resolveAnalysisDate(LocalDate analysisDate) {
        return analysisDate == null ? LocalDate.now(KOREA_ZONE_ID) : analysisDate;
    }

    private int resolveTopN(int topN) {
        if (topN < 1) {
            return DEFAULT_TOP_N;
        }
        return Math.min(topN, MAX_TOP_N);
    }
}
