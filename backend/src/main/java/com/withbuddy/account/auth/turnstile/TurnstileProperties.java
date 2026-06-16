package com.withbuddy.account.auth.turnstile;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security.turnstile")
public class TurnstileProperties {

    private boolean enabled = false;
    private String secretKey;
    private String siteverifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
}
