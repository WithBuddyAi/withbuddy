package com.withbuddy.infrastructure.ai.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class AiNoResultSummaryClient {

    private final RestClient restClient;

    public AiNoResultSummaryClient(
            @Value("${ai.server.base-url}") String aiBaseUrl,
            @Value("${ai.server.summary.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${ai.server.summary.read-timeout-ms:10000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(aiBaseUrl.endsWith("/") ? aiBaseUrl : aiBaseUrl + "/")
                .build();
    }

    public Top5AnalysisResponse analyzeTop5(String companyCode, List<String> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("questions는 비어 있을 수 없습니다.");
        }

        Top5AnalysisResponse response = restClient.post()
                .uri("knowledge/no-result/top5-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Top5AnalysisRequest(resolveCompanyCode(companyCode), questions))
                .retrieve()
                .body(Top5AnalysisResponse.class);

        if (response == null) {
            throw new IllegalStateException("AI TOP5 분석 응답이 비어 있습니다.");
        }

        return response;
    }

    private String resolveCompanyCode(String companyCode) {
        return StringUtils.hasText(companyCode) ? companyCode.trim() : "ALL";
    }

    private record Top5AnalysisRequest(
            String companyCode,
            List<String> questions
    ) {
    }

    public record Top5AnalysisResponse(
            String companyCode,
            String summary,
            List<Top5Action> actions,
            boolean hasSensitive
    ) {
        public Top5AnalysisResponse {
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }

    public record Top5Action(
            String part,
            String items
    ) {
    }
}
