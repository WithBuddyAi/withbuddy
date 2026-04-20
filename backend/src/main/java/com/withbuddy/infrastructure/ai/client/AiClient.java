package com.withbuddy.infrastructure.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.withbuddy.infrastructure.ai.dto.AiAnswerServerRequest;
import com.withbuddy.infrastructure.ai.dto.AiAnswerServerResponse;
import com.withbuddy.infrastructure.ai.exception.AiTimeoutException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final RestClient aiRestClient;
    private final ObjectMapper objectMapper;

    public AiAnswerServerResponse requestAnswer(AiAnswerServerRequest request) {
        try {
            String json = toJson(request);

            AiAnswerServerResponse response = aiRestClient.post()
                    .uri("/internal/ai/answer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(AiAnswerServerResponse.class);

            if (response == null) {
                throw new IllegalStateException("AI 서버 응답이 비어 있습니다.");
            }

            return response;

        } catch (ResourceAccessException e) {
            throw new AiTimeoutException("AI 서버 응답 시간이 초과되었습니다.", e);

        } catch (RestClientResponseException e) {
            log.error(
                    "AI 서버 응답 오류: status={}, body={}, requestQuestionId={}",
                    e.getRawStatusCode(),
                    e.getResponseBodyAsString(),
                    request.getQuestionId()
            );
            throw new RuntimeException("AI 서버 호출 중 오류가 발생했습니다.", e);

        } catch (RestClientException e) {
            throw new RuntimeException("AI 서버 호출 중 오류가 발생했습니다.", e);
        }
    }

    private String toJson(AiAnswerServerRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AI 요청 직렬화에 실패했습니다.", e);
        }
    }
}
