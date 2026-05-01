package com.withbuddy.global.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security.internal-api")
public class InternalApiSecurityProperties {

    private boolean enabled = true;
    private String headerName = "X-API-Key";
    private String token;
}
