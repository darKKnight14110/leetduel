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
    @Bean
    public RedisScript<List> tokenBucketScript() {
        return RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"), List.class);
    }
}
