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

    // List.class (a Class<List>, not Class<List<Object>>) is all a Class
    // token can express under erasure - there is no List<Object>.class to
    // write instead, so both the raw type and the unchecked cast below are
    // unavoidable, not sloppiness. Every field/parameter downstream is
    // declared as the fully-parameterized RedisScript<List<Object>>, so
    // this is the only place the raw type is ever named.
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Bean
    public RedisScript<List<Object>> pairMatchScript() {
        return (RedisScript) RedisScript.of(new ClassPathResource("scripts/pair_match.lua"), List.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Bean
    public RedisScript<List<Object>> leaveQueueScript() {
        return (RedisScript) RedisScript.of(new ClassPathResource("scripts/leave_queue.lua"), List.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Bean
    public RedisScript<List<Object>> expireJoinScript() {
        return (RedisScript) RedisScript.of(new ClassPathResource("scripts/expire_join.lua"), List.class);
    }
}
