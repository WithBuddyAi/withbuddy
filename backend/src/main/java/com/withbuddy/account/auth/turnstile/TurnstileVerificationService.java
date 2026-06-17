package com.withbuddy.account.auth.turnstile;

import com.withbuddy.account.auth.exception.TurnstileVerificationFailedException;
import com.withbuddy.account.auth.exception.TurnstileVerificationUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class TurnstileVerificationService {

    private static final String FAILED_MESSAGE = "보안 확인에 실패했습니다. 다시 시도해 주세요.";
    private static final String REQUIRED_MESSAGE = "보안 확인이 필요합니다. 다시 시도해 주세요.";
    private static final String UNAVAILABLE_MESSAGE = "보안 확인 서비스에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.";

    private final TurnstileProperties properties;
    private final TurnstileApiClient turnstileApiClient;

    public void verifyLoginToken(String token, String remoteIp) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(token)) {
            throw new TurnstileVerificationFailedException(REQUIRED_MESSAGE);
        }
        if (!StringUtils.hasText(properties.getSecretKey())) {
            throw new IllegalStateException("TURNSTILE_ENABLED=true 인데 TURNSTILE_SECRET_KEY가 비어 있습니다.");
        }

        try {
            TurnstileSiteverifyResponse response = turnstileApiClient.verify(properties.getSecretKey(), token, remoteIp);
            if (response == null || !response.success()) {
                throw new TurnstileVerificationFailedException(FAILED_MESSAGE);
            }
        } catch (TurnstileVerificationFailedException e) {
            throw e;
        } catch (RestClientException e) {
            throw new TurnstileVerificationUnavailableException(UNAVAILABLE_MESSAGE, e);
        }
    }
}
