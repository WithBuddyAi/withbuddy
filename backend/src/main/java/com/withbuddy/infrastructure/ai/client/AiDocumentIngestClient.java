package com.withbuddy.infrastructure.ai.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class AiDocumentIngestClient {

    private final RestClient restClient;
    private final String internalHeaderName;
    private final String internalKey;

    public AiDocumentIngestClient(
            @Value("${ai.server.base-url}") String aiBaseUrl,
            @Value("${ai.server.ingest.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${ai.server.ingest.read-timeout-ms:10000}") int readTimeoutMs,
            @Value("${ai.server.ingest.internal-header-name:X-Internal-Key}") String internalHeaderName,
            @Value("${ai.server.ingest.internal-key:${app.security.internal-api.token:}}") String internalKey
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(aiBaseUrl.endsWith("/") ? aiBaseUrl : aiBaseUrl + "/")
                .build();
        this.internalHeaderName = StringUtils.hasText(internalHeaderName)
                ? internalHeaderName
                : "X-Internal-Key";
        this.internalKey = internalKey;
    }

    public AiDocumentIngestResponse ingest(Long documentId, String companyCode) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId는 null일 수 없습니다.");
        }
        if (!StringUtils.hasText(companyCode)) {
            throw new IllegalArgumentException("companyCode는 비어 있을 수 없습니다.");
        }
        if (!StringUtils.hasText(internalKey)) {
            throw new IllegalStateException("AI 문서 인덱싱 내부 키가 설정되어 있지 않습니다.");
        }

        AiDocumentIngestResponse response = restClient.post()
                .uri("admin/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .header(internalHeaderName, internalKey)
                .body(new AiDocumentIngestRequest(documentId, companyCode))
                .retrieve()
                .body(AiDocumentIngestResponse.class);

        if (response == null) {
            throw new IllegalStateException("AI 문서 인덱싱 응답이 비어 있습니다.");
        }
        return response;
    }

    public AiDocumentDeindexResponse deindex(Long documentId, String companyCode) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId는 null일 수 없습니다.");
        }
        if (!StringUtils.hasText(companyCode)) {
            throw new IllegalArgumentException("companyCode는 비어 있을 수 없습니다.");
        }
        if (!StringUtils.hasText(internalKey)) {
            throw new IllegalStateException("AI 문서 인덱싱 내부 키가 설정되어 있지 않습니다.");
        }

        AiDocumentDeindexResponse response = restClient.method(HttpMethod.DELETE)
                .uri("admin/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .header(internalHeaderName, internalKey)
                .body(new AiDocumentIngestRequest(documentId, companyCode))
                .retrieve()
                .body(AiDocumentDeindexResponse.class);

        if (response == null) {
            throw new IllegalStateException("AI 문서 인덱스 삭제 응답이 비어 있습니다.");
        }
        return response;
    }

    private record AiDocumentIngestRequest(
            Long documentId,
            String companyCode
    ) {
    }

    public record AiDocumentIngestResponse(
            boolean success,
            Long documentId,
            Integer chunksIndexed
    ) {
    }

    public record AiDocumentDeindexResponse(
            boolean success,
            Long documentId
    ) {
    }
}
