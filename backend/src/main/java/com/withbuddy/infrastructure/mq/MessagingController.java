package com.withbuddy.infrastructure.mq;

import com.withbuddy.global.jwt.JwtService;
import com.withbuddy.infrastructure.mq.event.NudgeEvent;
import com.withbuddy.infrastructure.mq.event.NudgeType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/messaging")
public class MessagingController {

    private final MessagingQueueStatusService messagingQueueStatusService;
    private final NudgeEventPublisher nudgeEventPublisher;
    private final JwtService jwtService;

    @Value("${app.rabbitmq.test-endpoint-enabled:false}")
    private boolean testEndpointEnabled;

    @GetMapping("/queue/status")
    public ResponseEntity<Map<String, Object>> getQueueStatus() {
        return ResponseEntity.ok(messagingQueueStatusService.getStatus());
    }

    @PostMapping("/nudge/test")
    public ResponseEntity<Void> publishTestNudge(
            @RequestHeader("Authorization") String bearerToken,
            @RequestParam Long userId,
            @RequestParam(defaultValue = "false") boolean forceError
    ) {
        if (!testEndpointEnabled) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String token = jwtService.extractBearerToken(bearerToken);
        Long requesterUserId = jwtService.getUserId(token);
        if (!requesterUserId.equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!forceError && userId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        NudgeEvent event = new NudgeEvent(
                UUID.randomUUID().toString(),
                userId,
                null,
                forceError ? "__FORCE_ERROR__" : "[TEST] withbuddy nudge pipeline check",
                null,
                NudgeType.GENERAL,
                System.currentTimeMillis()
        );
        nudgeEventPublisher.publish(event);
        return ResponseEntity.ok().build();
    }
}

