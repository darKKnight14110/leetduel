package com.leetduel.submission.config;

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

    // Producer side of job dispatch - direct, not topic, since exactly one
    // consumer group (Judge Worker's pool) ever wants a job. Judge Worker's
    // own RabbitConfig independently redeclares this same exchange plus the
    // judge.jobs queue and binding (consumer side) - RabbitMQ dedups
    // identical declarations, same pattern as auth/user-service's
    // user.events exchange.
    @Bean
    public DirectExchange judgeJobsExchange(@Value("${leetduel.events.judge-jobs-exchange}") String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
    }

    // Consumer side of result publication - Judge Worker declares this
    // TopicExchange as producer; this service declares its OWN queue and
    // binding here, exactly like user-service binds its own queue to
    // auth-service's user.events exchange.
    @Bean
    public TopicExchange judgeEventsExchange(@Value("${leetduel.events.judge-events-exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue submissionJudgedQueue(@Value("${leetduel.events.submission-judged-queue}") String queueName) {
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
