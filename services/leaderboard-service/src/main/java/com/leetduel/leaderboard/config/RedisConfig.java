package com.leetduel.leaderboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

// StringRedisTemplate is autoconfigured by spring-boot-starter-data-redis.
// This class's only job is loading increment_period_score.lua as a
// cacheable RedisScript bean, same shape as matchmaking-service's
// RedisConfig / the Gateway's RedisScriptConfig.
@Configuration
public class RedisConfig {

    // List.class (a Class<List>, not Class<List<Object>>) is all a Class
    // token can express under erasure - see matchmaking-service's
    // RedisConfig for the full reasoning on why the raw type + unchecked
    // cast here is unavoidable, not sloppiness.
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Bean
    public RedisScript<List<Object>> incrementPeriodScoreScript() {
        return (RedisScript) RedisScript.of(new ClassPathResource("scripts/increment_period_score.lua"), List.class);
    }
}
