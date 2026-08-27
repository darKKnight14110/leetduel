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

    // match.events - matchmaking-service (match.created) and duel-service
    // (duel.progress, match.completed) are the producers; this service
    // only ever cares about match.completed, to apply the ELO delta and
    // duel W/L/D counters (see MatchCompletedListener). Redeclaring the
    // exchange here is the same "every consumer redeclares, RabbitMQ
    // dedups" pattern as userEventsExchange above.
    @Bean
    public TopicExchange matchEventsExchange(@Value("${leetduel.events.match-events-exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue matchCompletedQueue(@Value("${leetduel.events.match-completed-queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding matchCompletedBinding(Queue matchCompletedQueue, TopicExchange matchEventsExchange,
            @Value("${leetduel.events.match-completed-routing-key}") String routingKey) {
        return BindingBuilder.bind(matchCompletedQueue).to(matchEventsExchange).with(routingKey);
    }

    // See auth-service's RabbitConfig for why JacksonJsonMessageConverter,
    // not the deprecated Jackson2 variant.
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
