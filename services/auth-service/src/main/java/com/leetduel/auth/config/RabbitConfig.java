package com.leetduel.auth.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    // Topic exchange, not direct/fanout: matches the routing-key pattern
    // already used for match.* events elsewhere in the system, and lets
    // future consumers (e.g. a welcome-email service) bind to "user.created"
    // without any change here.
    @Bean
    public TopicExchange userEventsExchange(@Value("${leetduel.events.user-exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    // JSON over the wire, not Java serialization (Spring AMQP's default) -
    // keeps messages readable in the RabbitMQ management UI and doesn't tie
    // consumers to this JVM's serialVersionUID. JacksonJsonMessageConverter
    // (not the deprecated Jackson2* variant) - Spring Boot 4's default
    // ObjectMapper is Jackson 3 (tools.jackson.databind), and the Jackson2
    // converter needs classes from the legacy com.fasterxml.jackson.databind
    // line that Jackson 3 doesn't ship.
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
