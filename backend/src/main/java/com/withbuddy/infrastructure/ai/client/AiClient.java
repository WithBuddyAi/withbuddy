package com.withbuddy.infrastructure.ai.client;

import com.withbuddy.infrastructure.ai.dto.AiAnswerServerRequest;
import com.withbuddy.infrastructure.ai.dto.AiAnswerServerResponse;
import com.withbuddy.infrastructure.ai.exception.AiTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiClient {

    private final RestClient aiRestClient;

    public AiAnswerServerResponse requestAnswer(AiAnswerServerRequest request) {
        try {
            AiAnswerServerResponse response = aiRestClient.post()
                    .uri("/internal/ai/answer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiAnswerServerResponse.class);

            if (response == null) {
                throw new IllegalStateException("AI 서버 응답이 비어 있습니다.");
            }
            return response;
        } catch (ResourceAccessException e) {
            throw new AiTimeoutException("AI 서버 응답 시간이 초과되었습니다.", e);
        } catch (HttpClientErrorException e) {
            log.error("AI call failed. status={}, body={}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString(),
                    e);
            throw new RuntimeException("AI 서버 호출 중 오류가 발생했습니다.", e);
        } catch (RestClientException e) {
            log.error("AI call failed", e);
            throw new RuntimeException("AI 서버 호출 중 오류가 발생했습니다.", e);
        }
    }
}