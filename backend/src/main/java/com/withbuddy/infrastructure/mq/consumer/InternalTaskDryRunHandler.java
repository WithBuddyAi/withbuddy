package com.withbuddy.infrastructure.mq.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.withbuddy.internal.api.InternalTaskApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "app.internal-task-consumer", name = "dry-run", havingValue = "true", matchIfMissing = true)
public class InternalTaskDryRunHandler implements InternalTaskHandler {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String taskType) {
        return true;
    }

    @Override
    public JsonNode handle(InternalTaskApiService.InternalTaskMessage message) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("dryRun", true);
        result.put("taskId", message.taskId());
        result.put("type", message.type());
        result.put("processedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        result.put("note", "InternalTaskConsumer dry-run mode: side effects are not executed.");
        return result;
    }
}
