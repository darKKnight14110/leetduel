package com.leetduel.user.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange userEventsExchange(@Value("${leetduel.events.user-exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    // Durable queue: survives a RabbitMQ restart, and messages published
    // while this service is down are still waiting when it comes back
    // (at-least-once, not fire-and-forget).
    @Bean
    public Queue userCreatedQueue(@Value("${leetduel.events.user-created-queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding userCreatedBinding(Queue userCreatedQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(userCreatedQueue).to(userEventsExchange).with("user.created");
    }

    // See auth-service's RabbitConfig for why JacksonJsonMessageConverter,
    // not the deprecated Jackson2 variant.
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
