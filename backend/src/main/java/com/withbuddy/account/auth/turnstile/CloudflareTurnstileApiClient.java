package com.withbuddy.account.auth.turnstile;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class CloudflareTurnstileApiClient implements TurnstileApiClient {

    private final TurnstileProperties properties;
    private final RestClient restClient;

    public CloudflareTurnstileApiClient(TurnstileProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public TurnstileSiteverifyResponse verify(String secretKey, String token, String remoteIp) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("secret", secretKey);
        body.add("response", token);
        if (remoteIp != null && !remoteIp.isBlank()) {
            body.add("remoteip", remoteIp);
        }

        TurnstileSiteverifyResponse response = restClient.post()
                .uri(properties.getSiteverifyUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(TurnstileSiteverifyResponse.class);

        if (response == null) {
            return new TurnstileSiteverifyResponse(false, List.of("empty-response"));
        }

        return response;
    }
}
