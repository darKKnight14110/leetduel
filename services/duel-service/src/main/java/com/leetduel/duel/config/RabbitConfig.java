package com.leetduel.duel.config;

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

    // match.events - matchmaking-service declares this exchange as
    // producer; this service redeclares it (RabbitMQ dedups identical
    // declarations, same pattern as every cross-service exchange in this
    // repo) because it's BOTH a consumer here (match.created) AND, via
    // OutboxRelay, a producer of duel.progress/match.completed on the same
    // exchange.
    @Bean
    public TopicExchange matchEventsExchange(@Value("${leetduel.events.match-events-exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue matchCreatedQueue(@Value("${leetduel.events.duel-service-match-created-queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding matchCreatedBinding(Queue matchCreatedQueue, TopicExchange matchEventsExchange,
            @Value("${leetduel.events.match-created-routing-key}") String routingKey) {
        return BindingBuilder.bind(matchCreatedQueue).to(matchEventsExchange).with(routingKey);
    }

    // judge.events - Judge Worker's producer-side exchange, unchanged by
    // this binding (its own RabbitConfig comment anticipates exactly this
    // consumer). Consumer side only: this service never publishes here.
    @Bean
    public TopicExchange judgeEventsExchange(@Value("${leetduel.events.judge-events-exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue submissionJudgedQueue(
            @Value("${leetduel.events.duel-service-submission-judged-queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding submissionJudgedBinding(Queue submissionJudgedQueue, TopicExchange judgeEventsExchange,
            @Value("${leetduel.events.submission-judged-routing-key}") String routingKey) {
        return BindingBuilder.bind(submissionJudgedQueue).to(judgeEventsExchange).with(routingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
