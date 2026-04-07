package com.withbuddy.infrastructure.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
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
    public Queue reportQueue(AppRabbitMqProperties properties) {
        return new Queue(properties.queueReport(), true);
    }

    @Bean
    public Queue deadLetterQueue(AppRabbitMqProperties properties) {
        return new Queue(properties.queueDlq(), true);
    }

    @Bean
    public Declarables appBindings(
            TopicExchange appExchange,
            Queue reportQueue,
            Queue deadLetterQueue,
            AppRabbitMqProperties properties
    ) {
        Binding reportBinding = BindingBuilder
                .bind(reportQueue)
                .to(appExchange)
                .with(properties.queueReport());

        Binding dlqBinding = BindingBuilder
                .bind(deadLetterQueue)
                .to(appExchange)
                .with(properties.queueDlq());

        return new Declarables(reportBinding, dlqBinding);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
