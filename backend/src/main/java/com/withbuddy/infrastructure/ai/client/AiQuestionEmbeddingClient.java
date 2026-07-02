package com.withbuddy.infrastructure.ai.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class AiQuestionEmbeddingClient {

    private final RestClient restClient;

    public AiQuestionEmbeddingClient(
            @Value("${ai.server.base-url}") String aiBaseUrl,
            @Value("${ai.server.embedding.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${ai.server.embedding.read-timeout-ms:10000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(aiBaseUrl.endsWith("/") ? aiBaseUrl : aiBaseUrl + "/")
                .build();
    }

    public QuestionEmbeddingResponse embedQuestion(String companyCode, String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("질문 내용이 비어 있습니다.");
        }

        QuestionEmbeddingResponse response = restClient.post()
                .uri("embeddings/question")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new QuestionEmbeddingRequest(companyCode == null ? "" : companyCode.trim(), content))
                .retrieve()
                .body(QuestionEmbeddingResponse.class);

        if (response == null) {
            throw new IllegalStateException("AI 질문 임베딩 응답이 비어 있습니다.");
        }

        return response;
    }

    private record QuestionEmbeddingRequest(
            String companyCode,
            String content
    ) {
    }

    public record QuestionEmbeddingResponse(
            String embeddingModel,
            Integer dimension,
            List<Double> embedding
    ) {
        public QuestionEmbeddingResponse {
            embedding = embedding == null ? List.of() : List.copyOf(embedding);
        }
    }
}
