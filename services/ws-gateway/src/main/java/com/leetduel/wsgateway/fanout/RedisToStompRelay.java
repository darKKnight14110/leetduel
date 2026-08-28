package com.leetduel.wsgateway.fanout;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

// Second hop: Redis Pub/Sub (every WS Gateway instance gets every message)
// -> this instance's local STOMP SimpleBroker. convertAndSend only actually
// pushes bytes to a session if THIS JVM currently holds a subscriber to
// that destination - instances with no locally-connected client for the
// match silently no-op. That local-subscription check is exactly what
// replaces the "Redis matchId -> connectionId lookup table" the original
// (protocol-agnostic) design sketch described: Spring's SimpleBroker
// already tracks per-instance topic subscriptions, so there's nothing left
// to look up.
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisToStompRelay implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, @Nullable byte[] pattern) {
        String rawJson = new String(message.getBody(), StandardCharsets.UTF_8);
        JsonNode node = objectMapper.readTree(rawJson);
        JsonNode matchIdNode = node.get("matchId");
        if (matchIdNode != null) {
            messagingTemplate.convertAndSend("/topic/duel/" + matchIdNode.asString(), rawJson);
            return;
        }
        JsonNode userIdNode = node.get("userId");
        if (userIdNode != null) {
            messagingTemplate.convertAndSendToUser(userIdNode.asText(), "/queue/practice", rawJson);
            return;
        }
        log.warn("Fanout message had no supported destination");
    }
}
