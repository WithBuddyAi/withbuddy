package com.withbuddy.ai.client;

import com.withbuddy.ai.dto.AiServerRequest;
import com.withbuddy.ai.dto.AiServerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class AiClient {

    private final RestClient aiRestClient;

    public AiServerResponse requestAnswer(AiServerRequest request) {
        try {
            AiServerResponse response = aiRestClient.post()
                    .uri("/internal/ai/answer")
                    .body(request)
                    .retrieve()
                    .body(AiServerResponse.class);

            if (response == null) {
                throw new IllegalStateException("AI 서버 응답이 비어 있습니다.");
            }

            return response;
        } catch (RestClientException e){
            throw new RuntimeException("AI 서버 호출 중 오류가 발생했습니다.", e);
        }
    }
}
