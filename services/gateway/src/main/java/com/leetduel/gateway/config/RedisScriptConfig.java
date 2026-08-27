package com.leetduel.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
public class RedisScriptConfig {

    // Loaded once at startup and cached by Redis server-side (EVALSHA under
    // the hood, handled transparently by RedisScript/RedisTemplate) rather
    // than shipping the full script text on every request.
    //
    // List.class (a Class<List>, not Class<List<Object>>) is all a Class
    // token can express under erasure - there is no List<Object>.class to
    // write instead, so both the raw type and the unchecked cast below are
    // unavoidable, not sloppiness. See matchmaking-service's RedisConfig
    // for the identical pattern.
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Bean
    public RedisScript<List<Object>> tokenBucketScript() {
        return (RedisScript) RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"), List.class);
    }
}
