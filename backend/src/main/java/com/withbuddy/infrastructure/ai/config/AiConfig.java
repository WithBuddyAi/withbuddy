package com.withbuddy.infrastructure.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiConfig {

    @Bean
    public RestClient aiRestClient(
            @Value("${ai.server.base-url}") String aiBaseUrl,
            @Value("${ai.server.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${ai.server.read-timeout-ms:30000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        return RestClient.builder()
                .baseUrl(aiBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
