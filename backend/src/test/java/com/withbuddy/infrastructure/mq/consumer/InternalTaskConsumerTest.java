package com.withbuddy.infrastructure.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.withbuddy.infrastructure.mq.AppRabbitMqProperties;
import com.withbuddy.infrastructure.mq.MessagingMetricsService;
import com.withbuddy.internal.api.InternalTaskApiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.context.RetryContextSupport;
import org.springframework.retry.support.RetrySynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalTaskConsumerTest {

    @Mock
    private InternalTaskApiService taskApiService;

    @Mock
    private MessagingMetricsService metricsService;

    @Mock
    private InternalTaskHandler taskHandler;

    @Mock
    private Channel channel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearRetryContext() {
        RetrySynchronizationManager.clear();
    }

    @Test
    void transientFailureDoesNotMarkFailedBeforeRetryExhausted() throws Exception {
        InternalTaskConsumer consumer = new InternalTaskConsumer(
                taskApiService,
                metricsService,
                objectMapper,
                List.of(taskHandler),
                new AppRabbitMqProperties("ex", "n", "a", "it", "it-dlq", 1, 1, 10, 3)
        );
        InternalTaskApiService.InternalTaskMessage message =
                new InternalTaskApiService.InternalTaskMessage("task-1", "callback", objectMapper.createObjectNode(), null, 30, 2, null);
        InternalTaskApiService.TaskState state =
                new InternalTaskApiService.TaskState("task-1", "callback", "QUEUED", objectMapper.createObjectNode(), null, null, null, 30, 2, "2026-07-01T00:00:00Z", "2026-07-01T00:00:00Z");

        RetryContextSupport context = new RetryContextSupport(null);
        context.registerThrowable(new RuntimeException("attempt1"));
        RetrySynchronizationManager.register(context);

        when(taskApiService.findState("task-1")).thenReturn(Optional.of(state));
        when(taskHandler.supports("callback")).thenReturn(true);
        when(taskHandler.handle(message)).thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> consumer.handleInternalTask(message, channel, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("processing failed");

        verify(taskApiService).markRunning("task-1");
        verify(taskApiService, never()).markFailed(any(), any());
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void finalFailureMarksTaskFailedAndPropagates() throws Exception {
        InternalTaskConsumer consumer = new InternalTaskConsumer(
                taskApiService,
                metricsService,
                objectMapper,
                List.of(taskHandler),
                new AppRabbitMqProperties("ex", "n", "a", "it", "it-dlq", 1, 1, 10, 3)
        );
        InternalTaskApiService.InternalTaskMessage message =
                new InternalTaskApiService.InternalTaskMessage("task-1", "callback", objectMapper.createObjectNode(), null, 30, 2, null);
        InternalTaskApiService.TaskState state =
                new InternalTaskApiService.TaskState("task-1", "callback", "QUEUED", objectMapper.createObjectNode(), null, null, null, 30, 2, "2026-07-01T00:00:00Z", "2026-07-01T00:00:00Z");

        RetryContextSupport context = new RetryContextSupport(null);
        context.registerThrowable(new RuntimeException("attempt1"));
        context.registerThrowable(new RuntimeException("attempt2"));
        RetrySynchronizationManager.register(context);

        when(taskApiService.findState("task-1")).thenReturn(Optional.of(state));
        when(taskHandler.supports("callback")).thenReturn(true);
        when(taskHandler.handle(message)).thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> consumer.handleInternalTask(message, channel, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("processing failed");

        verify(taskApiService).markRunning("task-1");
        verify(taskApiService).markFailed("task-1", "timeout");
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }
}
