package com.leetduel.wsgateway.config;

import com.leetduel.wsgateway.fanout.RedisToStompRelay;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

// First Redis Pub/Sub usage in this repo - every other Redis use
// (matchmaking's sorted set, the rate limiter's token bucket) is
// request-response against stored state. This is a fire-and-forget
// broadcast: every WS Gateway instance subscribes to the same channel, and
// each independently decides (via its own local SimpleBroker) whether it
// actually holds a session that cares. See RedisToStompRelay for the
// consuming side.
@Configuration
@RequiredArgsConstructor
public class RedisPubSubConfig {

    private final RedisToStompRelay redisToStompRelay;

    @Bean
    public ChannelTopic wsFanoutTopic(@Value("${leetduel.ws.fanout-channel}") String channel) {
        return new ChannelTopic(channel);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory, ChannelTopic wsFanoutTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(redisToStompRelay, wsFanoutTopic);
        return container;
    }
}
