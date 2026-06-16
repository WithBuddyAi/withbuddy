package com.withbuddy.account.auth.turnstile;

public interface TurnstileApiClient {

    TurnstileSiteverifyResponse verify(String secretKey, String token, String remoteIp);
}
