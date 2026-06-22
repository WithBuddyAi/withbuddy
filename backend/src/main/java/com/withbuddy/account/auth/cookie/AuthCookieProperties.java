package com.withbuddy.account.auth.cookie;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security.auth-cookie")
public class AuthCookieProperties {

    private String sameSite = "Lax";
    private String domain;
    private String path = "/";
    private boolean httpOnly = true;
    private Boolean secure;
}
