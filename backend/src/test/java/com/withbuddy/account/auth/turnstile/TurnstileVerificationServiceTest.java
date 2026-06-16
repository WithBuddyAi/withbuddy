package com.withbuddy.account.auth.turnstile;

import com.withbuddy.account.auth.exception.TurnstileVerificationFailedException;
import com.withbuddy.account.auth.exception.TurnstileVerificationUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TurnstileVerificationServiceTest {

    @Mock
    private TurnstileApiClient turnstileApiClient;

    private TurnstileProperties properties;
    private TurnstileVerificationService service;

    @BeforeEach
    void setUp() {
        properties = new TurnstileProperties();
        service = new TurnstileVerificationService(properties, turnstileApiClient);
    }

    @Test
    void skipsVerificationWhenDisabled() {
        properties.setEnabled(false);

        assertThatCode(() -> service.verifyLoginToken("", "127.0.0.1"))
                .doesNotThrowAnyException();

        verify(turnstileApiClient, never()).verify(anyString(), anyString(), anyString());
    }

    @Test
    void rejectsBlankTokenWhenEnabled() {
        properties.setEnabled(true);
        properties.setSecretKey("secret");

        assertThatThrownBy(() -> service.verifyLoginToken(" ", "127.0.0.1"))
                .isInstanceOf(TurnstileVerificationFailedException.class)
                .hasMessage("보안 확인이 필요합니다. 다시 시도해 주세요.");
    }

    @Test
    void rejectsFailedVerificationResponse() {
        properties.setEnabled(true);
        properties.setSecretKey("secret");
        when(turnstileApiClient.verify("secret", "token", "127.0.0.1"))
                .thenReturn(new TurnstileSiteverifyResponse(false, List.of("timeout-or-duplicate")));

        assertThatThrownBy(() -> service.verifyLoginToken("token", "127.0.0.1"))
                .isInstanceOf(TurnstileVerificationFailedException.class)
                .hasMessage("보안 확인에 실패했습니다. 다시 시도해 주세요.");
    }

    @Test
    void mapsProviderFailureToServiceUnavailable() {
        properties.setEnabled(true);
        properties.setSecretKey("secret");
        when(turnstileApiClient.verify("secret", "token", "127.0.0.1"))
                .thenThrow(new RestClientException("timeout"));

        assertThatThrownBy(() -> service.verifyLoginToken("token", "127.0.0.1"))
                .isInstanceOf(TurnstileVerificationUnavailableException.class)
                .hasMessage("보안 확인 서비스에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }

    @Test
    void passesWhenVerificationSucceeds() {
        properties.setEnabled(true);
        properties.setSecretKey("secret");
        when(turnstileApiClient.verify("secret", "token", "127.0.0.1"))
                .thenReturn(new TurnstileSiteverifyResponse(true, List.of()));

        assertThatCode(() -> service.verifyLoginToken("token", "127.0.0.1"))
                .doesNotThrowAnyException();

        verify(turnstileApiClient).verify("secret", "token", "127.0.0.1");
    }
}
