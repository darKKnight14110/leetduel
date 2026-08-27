package com.leetduel.matchmaking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

// StringRedisTemplate is autoconfigured by spring-boot-starter-data-redis
// (same way ReactiveStringRedisTemplate is autoconfigured for the reactive
// Gateway) - this class's only job is loading the three Lua scripts as
// cacheable RedisScript beans, same shape as the Gateway's
// RedisScriptConfig.
@Configuration
public class RedisConfig {

    @Bean
    public RedisScript<List> pairMatchScript() {
        return RedisScript.of(new ClassPathResource("scripts/pair_match.lua"), List.class);
    }

    @Bean
    public RedisScript<List> leaveQueueScript() {
        return RedisScript.of(new ClassPathResource("scripts/leave_queue.lua"), List.class);
    }

    @Bean
    public RedisScript<List> expireJoinScript() {
        return RedisScript.of(new ClassPathResource("scripts/expire_join.lua"), List.class);
    }
}
