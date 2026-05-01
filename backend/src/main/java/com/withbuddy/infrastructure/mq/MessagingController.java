package com.withbuddy.infrastructure.mq;

import com.withbuddy.infrastructure.mq.event.NudgeEvent;
import com.withbuddy.infrastructure.mq.event.NudgeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    @GetMapping("/queue/status")
    public ResponseEntity<Map<String, Object>> getQueueStatus() {
        return ResponseEntity.ok(messagingQueueStatusService.getStatus());
    }

    @PostMapping("/nudge/test")
    public ResponseEntity<Void> publishTestNudge(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "false") boolean forceError
    ) {
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

