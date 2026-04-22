package com.withbuddy.infrastructure.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppRabbitMqProperties.class)
public class RabbitMqConfig {

    @Bean
    public TopicExchange appExchange(AppRabbitMqProperties properties) {
        return new TopicExchange(properties.exchange(), true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange(AppRabbitMqProperties properties) {
        return new DirectExchange(properties.dlxExchange(), true, false);
    }

    @Bean
    public Queue reportQueue(AppRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.queueReport())
                .withArgument("x-dead-letter-exchange", properties.dlxExchange())
                .withArgument("x-dead-letter-routing-key", properties.queueDlq())
                .build();
    }

    @Bean
    public Queue nudgeQueue(AppRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.queueNudge())
                .withArgument("x-dead-letter-exchange", properties.dlxExchange())
                .withArgument("x-dead-letter-routing-key", properties.queueDlqNudge())
                .withArgument("x-message-ttl", properties.nudgeTtlMs())
                .build();
    }

    @Bean
    public Queue analyticsQueue(AppRabbitMqProperties properties) {
        return QueueBuilder.durable(properties.queueAnalytics())
                .withArgument("x-dead-letter-exchange", properties.dlxExchange())
                .withArgument("x-dead-letter-routing-key", properties.queueDlqAnalytics())
                .build();
    }

    @Bean
    public Queue deadLetterReportQueue(AppRabbitMqProperties properties) {
        return new Queue(properties.queueDlq(), true);
    }

    @Bean
    public Queue deadLetterNudgeQueue(AppRabbitMqProperties properties) {
        return new Queue(properties.queueDlqNudge(), true);
    }

    @Bean
    public Queue deadLetterAnalyticsQueue(AppRabbitMqProperties properties) {
        return new Queue(properties.queueDlqAnalytics(), true);
    }

    @Bean
    public Declarables appBindings(
            TopicExchange appExchange,
            DirectExchange deadLetterExchange,
            Queue reportQueue,
            Queue nudgeQueue,
            Queue analyticsQueue,
            Queue deadLetterReportQueue,
            Queue deadLetterNudgeQueue,
            Queue deadLetterAnalyticsQueue,
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

        Binding reportDlqBinding = BindingBuilder
                .bind(deadLetterReportQueue)
                .to(deadLetterExchange)
                .with(properties.queueDlq());
        Binding nudgeDlqBinding = BindingBuilder
                .bind(deadLetterNudgeQueue)
                .to(deadLetterExchange)
                .with(properties.queueDlqNudge());
        Binding analyticsDlqBinding = BindingBuilder
                .bind(deadLetterAnalyticsQueue)
                .to(deadLetterExchange)
                .with(properties.queueDlqAnalytics());

        return new Declarables(
                reportBinding,
                nudgeBinding,
                analyticsBinding,
                reportDlqBinding,
                nudgeDlqBinding,
                analyticsDlqBinding
        );
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
