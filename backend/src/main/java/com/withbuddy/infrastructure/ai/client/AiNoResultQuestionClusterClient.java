package com.withbuddy.infrastructure.ai.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

@Component
public class AiNoResultQuestionClusterClient {

    private final RestClient restClient;

    public AiNoResultQuestionClusterClient(
            @Value("${ai.server.base-url}") String aiBaseUrl,
            @Value("${ai.server.cluster.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${ai.server.cluster.read-timeout-ms:20000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(aiBaseUrl.endsWith("/") ? aiBaseUrl : aiBaseUrl + "/")
                .build();
    }

    public ClusterResponse clusterQuestions(
            String companyCode,
            LocalDate analysisDate,
            int topN,
            List<ClusterItemRequest> items
    ) {
        ClusterResponse response = restClient.post()
                .uri("clusters/no-result/questions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ClusterRequest(companyCode, analysisDate.toString(), topN, items))
                .retrieve()
                .body(ClusterResponse.class);

        if (response == null) {
            throw new IllegalStateException("AI no_result 질문 군집화 응답이 비어 있습니다.");
        }
        return response;
    }

    private record ClusterRequest(
            String companyCode,
            String analysisDate,
            int topN,
            List<ClusterItemRequest> items
    ) {
    }

    public record ClusterItemRequest(
            Long logId,
            String questionContent
    ) {
    }

    public record ClusterResponse(
            String companyCode,
            LocalDate analysisDate,
            int sourceCount,
            List<TopQuestion> topQuestions,
            AiSummary aiSummary
    ) {
        public ClusterResponse {
            topQuestions = topQuestions == null ? List.of() : List.copyOf(topQuestions);
        }
    }

    public record TopQuestion(
            int rank,
            String questionContent,
            long count
    ) {
    }

    public record AiSummary(
            String status,
            String summary,
            List<Action> actions,
            boolean hasSensitive,
            String errorMessage
    ) {
        public AiSummary {
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }

    public record Action(
            String part,
            String items
    ) {
    }
}
