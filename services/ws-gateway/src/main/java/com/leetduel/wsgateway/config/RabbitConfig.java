package com.leetduel.wsgateway.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Consumer side only - matchmaking-service and duel-service are the
// producers on match.events; this service redeclares the exchange
// (RabbitMQ dedups identical declarations, same pattern as every
// cross-service exchange in this repo) and binds its OWN queue with three
// separate bindings, one per routing key it cares about. No custom
// MessageConverter bean here - see RabbitToRedisRelay's comment on why
// this listener deliberately consumes raw JSON, not typed DTOs.
@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange matchEventsExchange(@Value("${leetduel.events.match-events-exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue wsGatewayQueue(@Value("${leetduel.events.ws-gateway-queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding matchCreatedBinding(Queue wsGatewayQueue, TopicExchange matchEventsExchange,
            @Value("${leetduel.events.match-created-routing-key}") String routingKey) {
        return BindingBuilder.bind(wsGatewayQueue).to(matchEventsExchange).with(routingKey);
    }

    @Bean
    public Binding duelProgressBinding(Queue wsGatewayQueue, TopicExchange matchEventsExchange,
            @Value("${leetduel.events.duel-progress-routing-key}") String routingKey) {
        return BindingBuilder.bind(wsGatewayQueue).to(matchEventsExchange).with(routingKey);
    }

    @Bean
    public Binding matchCompletedBinding(Queue wsGatewayQueue, TopicExchange matchEventsExchange,
            @Value("${leetduel.events.match-completed-routing-key}") String routingKey) {
        return BindingBuilder.bind(wsGatewayQueue).to(matchEventsExchange).with(routingKey);
    }
}
