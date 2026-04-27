package com.withbuddy.chat.service;

import com.withbuddy.infrastructure.ai.dto.AiAnswerServerRequest;
import com.withbuddy.infrastructure.ai.dto.AiAnswerServerResponse;
import com.withbuddy.infrastructure.ai.exception.AiTimeoutException;
import com.withbuddy.infrastructure.redis.RedisCacheService;
import com.withbuddy.infrastructure.redis.RedisCacheTtl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;

@Service
@Slf4j
public class AsyncAiCallService {

    private final RestClient asyncAiRestClient;
    private final ChatAnswerSaveService chatAnswerSaveService;
    private final RedisCacheService redisCacheService;

    public AsyncAiCallService(
            @Qualifier("asyncAiRestClient") RestClient asyncAiRestClient,
            ChatAnswerSaveService chatAnswerSaveService,
            RedisCacheService redisCacheService
    ) {
        this.asyncAiRestClient = asyncAiRestClient;
        this.chatAnswerSaveService = chatAnswerSaveService;
        this.redisCacheService = redisCacheService;
    }

    @Async("aiCallExecutor")
    public void callAndSaveAnswer(Long questionId, Long userId, AiAnswerServerRequest aiRequest) {
        log.info("비동기 AI 호출 시작: questionId={}", questionId);
        try {
            AiAnswerServerResponse aiResponse = requestAnswer(aiRequest);
            chatAnswerSaveService.saveAsyncAnswer(questionId, userId, aiResponse);
            log.info("비동기 AI 호출 완료: questionId={}", questionId);
        } catch (Exception e) {
            log.error("비동기 AI 호출 실패: questionId={}", questionId, e);
            redisCacheService.put(ragStatusKey(questionId), "TIMEOUT", RedisCacheTtl.RAG_STATUS);
        }
    }

    private AiAnswerServerResponse requestAnswer(AiAnswerServerRequest request) {
        try {
            AiAnswerServerResponse response = asyncAiRestClient.post()
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

        } catch (HttpClientErrorException e) {
            log.error("비동기 AI 호출 오류: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("AI 서버 호출 중 오류가 발생했습니다.", e);

        } catch (ResourceAccessException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                log.warn("비동기 AI timeout (55s 초과)", e);
                throw new AiTimeoutException("비동기 AI 응답 시간이 초과되었습니다.", e);
            }
            if (cause instanceof ConnectException || cause instanceof UnknownHostException) {
                log.error("비동기 AI 서버 연결 실패", e);
                throw new RuntimeException("AI 서버 연결에 실패했습니다.", e);
            }
            log.error("비동기 AI 네트워크 오류", e);
            throw new RuntimeException("AI 서버 호출 중 네트워크 오류가 발생했습니다.", e);

        } catch (RestClientException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                log.warn("비동기 AI timeout (RestClientException)", e);
                throw new AiTimeoutException("비동기 AI 응답 시간이 초과되었습니다.", e);
            }
            log.error("비동기 AI 호출 실패", e);
            throw new RuntimeException("AI 서버 호출 중 오류가 발생했습니다.", e);
        }
    }

    static String ragStatusKey(Long questionId) {
        return "rag:status:" + questionId;
    }

    static String ragAnswerKey(Long questionId) {
        return "rag:answer:" + questionId;
    }
}
