package com.leetduel.leaderboard.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Consumer side only - matchmaking-service and duel-service are the
// producers on match.events; this service redeclares the exchange
// (RabbitMQ dedups identical declarations, same pattern as every
// cross-service exchange in this repo) and binds its own queue, one
// binding, since it only ever cares about match.completed (unlike
// ws-gateway, which also needs match.created/duel.progress).
@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange matchEventsExchange(@Value("${leetduel.events.match-events-exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue leaderboardMatchCompletedQueue(
            @Value("${leetduel.events.leaderboard-match-completed-queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding matchCompletedBinding(Queue leaderboardMatchCompletedQueue, TopicExchange matchEventsExchange,
            @Value("${leetduel.events.match-completed-routing-key}") String routingKey) {
        return BindingBuilder.bind(leaderboardMatchCompletedQueue).to(matchEventsExchange).with(routingKey);
    }

    // See auth-service's RabbitConfig for why JacksonJsonMessageConverter,
    // not the deprecated Jackson2 variant.
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
