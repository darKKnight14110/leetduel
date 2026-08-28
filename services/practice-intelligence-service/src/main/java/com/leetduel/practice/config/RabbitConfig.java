package com.leetduel.practice.config;

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
    TopicExchange practiceExchange(@Value("${leetduel.events.practice-exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    Queue practiceQueue(@Value("${leetduel.events.practice-queue}") String name) {
        return new Queue(name, true);
    }

    @Bean
    Binding practiceBinding(Queue practiceQueue, TopicExchange practiceExchange,
            @Value("${leetduel.events.practice-routing-key}") String routingKey) {
        return BindingBuilder.bind(practiceQueue).to(practiceExchange).with(routingKey);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
