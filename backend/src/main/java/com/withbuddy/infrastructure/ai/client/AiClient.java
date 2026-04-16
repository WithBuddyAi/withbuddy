package com.withbuddy.infrastructure.ai.client;

import com.withbuddy.infrastructure.ai.dto.AiAnswerServerRequest;
import com.withbuddy.infrastructure.ai.dto.AiAnswerServerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
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
        } catch (RestClientException e){
            throw new RuntimeException("AI 서버 호출 중 오류가 발생했습니다.", e);
        }
    }
}
