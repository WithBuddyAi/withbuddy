package com.withbuddy.infrastructure.ai.client;

import org.springframework.beans.factory.annotation.Value;
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
            throw new IllegalArgumentException("documentId must not be null.");
        }
        if (!StringUtils.hasText(companyCode)) {
            throw new IllegalArgumentException("companyCode must not be blank.");
        }
        if (!StringUtils.hasText(internalKey)) {
            throw new IllegalStateException("AI document ingest internal key is not configured.");
        }

        AiDocumentIngestResponse response = restClient.post()
                .uri("admin/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .header(internalHeaderName, internalKey)
                .body(new AiDocumentIngestRequest(documentId, companyCode))
                .retrieve()
                .body(AiDocumentIngestResponse.class);

        if (response == null) {
            throw new IllegalStateException("AI document ingest response is empty.");
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
}
