package com.leetduel.wsgateway.fanout;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

// First hop: RabbitMQ (competing-consumer queue - exactly ONE running
// instance receives each event) -> Redis Pub/Sub (broadcast to every
// instance, including this one). Deliberately consumes the raw JSON body,
// not a typed DTO - this service never needs to interpret match.created /
// duel.progress / match.completed beyond a matchId field (parsed on the
// SECOND hop, see RedisToStompRelay), so there's no reason to maintain
// three more independent event-record copies here.
@Component
@RequiredArgsConstructor
public class RabbitToRedisRelay {

    private final StringRedisTemplate redisTemplate;

    @Value("${leetduel.ws.fanout-channel}")
    private String fanoutChannel;

    // Takes the raw AMQP Message rather than letting a MessageConverter
    // deserialize it - this listener declares no MessageConverter bean at
    // all (see RabbitConfig), so relying on Spring AMQP's default
    // conversion behavior for a bare String parameter would be guessing at
    // its content-type handling. Reading the body bytes directly is
    // unambiguous.
    @RabbitListener(queues = "${leetduel.events.ws-gateway-queue}")
    public void onMatchEvent(Message message) {
        String rawJsonPayload = new String(message.getBody(), StandardCharsets.UTF_8);
        redisTemplate.convertAndSend(fanoutChannel, rawJsonPayload);
    }
}
