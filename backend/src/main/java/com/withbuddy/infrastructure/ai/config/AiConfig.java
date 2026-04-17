package com.withbuddy.infrastructure.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AiConfig {

    @Bean
    public RestClient aiRestClient(@Value("${ai.server.base-url}") String aiBaseUrl) {
        return RestClient.builder()
                .baseUrl(aiBaseUrl)
                .build();
    }
}
