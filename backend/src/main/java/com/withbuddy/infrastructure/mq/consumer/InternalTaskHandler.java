package com.withbuddy.infrastructure.mq.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.withbuddy.internal.api.InternalTaskApiService;

public interface InternalTaskHandler {

    boolean supports(String taskType);

    JsonNode handle(InternalTaskApiService.InternalTaskMessage message);
}
