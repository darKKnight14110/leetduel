package com.leetduel.matchmaking.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    // Direct exchange - exactly one consumer group (this service's own
    // JoinRequestListener) ever wants a join-request message, same
    // reasoning as submission-service's judge.jobs.exchange.
    @Bean
    public DirectExchange matchmakingJobsExchange(
            @Value("${leetduel.events.matchmaking-jobs-exchange}") String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Queue matchmakingJoinQueue(@Value("${leetduel.events.matchmaking-join-queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding matchmakingJoinBinding(Queue matchmakingJoinQueue, DirectExchange matchmakingJobsExchange,
            @Value("${leetduel.events.matchmaking-join-routing-key}") String routingKey) {
        return BindingBuilder.bind(matchmakingJoinQueue).to(matchmakingJobsExchange).with(routingKey);
    }

    // Producer side only - Duel Service/WS Gateway/Leaderboard Service
    // (Phase 3+) each declare their own queue and binding against this
    // exchange independently, exactly like Judge Worker produces
    // judge.events without declaring submission-service's consumer queue.
    @Bean
    public TopicExchange matchEventsExchange(@Value("${leetduel.events.match-events-exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
