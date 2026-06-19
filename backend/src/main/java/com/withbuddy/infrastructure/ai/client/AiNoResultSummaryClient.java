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
    private final String promptStyle;

    public AiNoResultSummaryClient(
            @Value("${ai.server.base-url}") String aiBaseUrl,
            @Value("${ai.server.summary.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${ai.server.summary.read-timeout-ms:10000}") int readTimeoutMs,
            @Value("${ai.server.summary.prompt-style:A}") String promptStyle
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(aiBaseUrl.endsWith("/") ? aiBaseUrl : aiBaseUrl + "/")
                .build();
        this.promptStyle = StringUtils.hasText(promptStyle) ? promptStyle.trim() : "A";
    }

    public NoResultSummaryResponse summarize(String companyCode, List<String> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("questions는 비어 있을 수 없습니다.");
        }

        NoResultSummaryResponse response = restClient.post()
                .uri("knowledge/no-result/summary")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new NoResultSummaryRequest(resolveCompanyCode(companyCode), questions, promptStyle))
                .retrieve()
                .body(NoResultSummaryResponse.class);

        if (response == null) {
            throw new IllegalStateException("AI 요약 응답이 비어 있습니다.");
        }

        return response;
    }

    private String resolveCompanyCode(String companyCode) {
        return StringUtils.hasText(companyCode) ? companyCode.trim() : "ALL";
    }

    private record NoResultSummaryRequest(
            String companyCode,
            List<String> questions,
            String promptStyle
    ) {
    }

    public record NoResultSummaryResponse(
            String companyCode,
            int questionCount,
            String summary,
            List<String> actions,
            String promptStyle
    ) {
        public NoResultSummaryResponse {
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }
}
