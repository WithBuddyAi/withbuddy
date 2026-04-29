package com.withbuddy.infrastructure.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@Configuration
@EnableRabbit
@Slf4j
@EnableConfigurationProperties(AppRabbitMqProperties.class)
public class RabbitMqConfig {

    @Bean
    public TopicExchange appExchange(AppRabbitMqProperties properties) {
        return new TopicExchange(properties.exchange(), true, false);
    }

    @Bean
    public Queue reportQueue(AppRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.queueReport()).build();
    }

    @Bean
    public Queue nudgeQueue(AppRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.queueNudge())
                .withArgument("x-message-ttl", properties.nudgeTtlMs())
                .build();
    }

    @Bean
    public Queue analyticsQueue(AppRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.queueAnalytics())
                .withArgument("x-message-ttl", intOrDefault(properties.analyticsTtlMs(), 30000))
                .build();
    }

    @Bean
    public Declarables appBindings(
            TopicExchange appExchange,
            Queue reportQueue,
            Queue nudgeQueue,
            Queue analyticsQueue,
            AppRabbitMqProperties properties
    ) {
        Binding reportBinding = BindingBuilder
                .bind(reportQueue)
                .to(appExchange)
                .with(properties.queueReport());
        Binding nudgeBinding = BindingBuilder
                .bind(nudgeQueue)
                .to(appExchange)
                .with("nudge.#");
        Binding analyticsBinding = BindingBuilder
                .bind(analyticsQueue)
                .to(appExchange)
                .with("analytics.#");

        return new Declarables(
                reportBinding,
                nudgeBinding,
                analyticsBinding
        );
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("[RMQ] publish confirm failed. cause={}, correlationData={}", cause, correlationData);
            }
        });
        template.setReturnsCallback(returned -> log.error(
                "[RMQ] unroutable message. exchange={}, routingKey={}, replyCode={}, replyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText()
        ));
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            AppRabbitMqProperties properties
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(intOrDefault(properties.listenerPrefetch(), 10));
        factory.setAdviceChain(retryInterceptor(properties));
        return factory;
    }

    private RetryOperationsInterceptor retryInterceptor(AppRabbitMqProperties properties) {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(intOrDefault(properties.listenerMaxAttempts(), 3))
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    private int intOrDefault(Integer value, int fallback) {
        return value != null ? value : fallback;
    }
}
