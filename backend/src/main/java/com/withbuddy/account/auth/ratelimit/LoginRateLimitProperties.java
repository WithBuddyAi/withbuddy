package com.withbuddy.account.auth.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security.login-rate-limit")
public class LoginRateLimitProperties {

    private boolean enabled = true;
    private int accountMaxFailures = 5;
    private int ipMaxFailures = 20;
    private int windowSeconds = 600;
    private int lockSeconds = 900;
}
