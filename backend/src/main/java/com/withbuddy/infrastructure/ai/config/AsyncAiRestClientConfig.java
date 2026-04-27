package com.withbuddy.infrastructure.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AsyncAiRestClientConfig {

    @Bean("asyncAiRestClient")
    public RestClient asyncAiRestClient(
            @Value("${ai.server.base-url}") String aiBaseUrl,
            @Value("${ai.server.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${ai.server.async-read-timeout-ms:55000}") int asyncReadTimeoutMs
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(asyncReadTimeoutMs);

        return RestClient.builder()
                .baseUrl(aiBaseUrl)
                .requestFactory(factory)
                .build();
    }
}
