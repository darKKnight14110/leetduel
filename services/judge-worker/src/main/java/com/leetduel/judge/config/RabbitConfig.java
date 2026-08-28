package com.leetduel.judge.config;

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
import org.springframework.context.annotation.Profile;

@Configuration
@Profile({"docker", "dispatcher"})
public class RabbitConfig {

    // Consumer side of job dispatch - redeclares the same direct exchange
    // submission-service's OutboxRelay publishes to (RabbitMQ dedups
    // identical declarations), plus the queue and binding submission-service
    // does NOT declare, since only this service's worker pool ever consumes
    // a job.
    @Bean
    public DirectExchange judgeJobsExchange(@Value("${leetduel.events.judge-jobs-exchange}") String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Queue judgeJobsQueue(@Value("${leetduel.events.judge-jobs-queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding judgeJobsBinding(Queue judgeJobsQueue, DirectExchange judgeJobsExchange,
            @Value("${leetduel.events.judge-jobs-routing-key}") String routingKey) {
        return BindingBuilder.bind(judgeJobsQueue).to(judgeJobsExchange).with(routingKey);
    }

    // Producer side of result publication - topic, not direct, even with
    // only one bound consumer (submission-service) today. Phase 3's Duel
    // Service binding its own queue to submission.judged later is zero
    // topology change here.
    @Bean
    public TopicExchange judgeEventsExchange(@Value("${leetduel.events.judge-events-exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
