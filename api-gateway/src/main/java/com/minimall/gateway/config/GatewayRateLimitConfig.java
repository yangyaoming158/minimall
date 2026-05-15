package com.minimall.gateway.config;

import com.minimall.gateway.ratelimit.GatewayRateLimiter;
import com.minimall.gateway.ratelimit.RedisGatewayRateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Configuration
@EnableConfigurationProperties(GatewayRateLimitProperties.class)
public class GatewayRateLimitConfig {

    @Bean
    @ConditionalOnMissingBean
    public GatewayRateLimiter gatewayRateLimiter(
            ReactiveStringRedisTemplate redisTemplate,
            GatewayRateLimitProperties properties) {
        return new RedisGatewayRateLimiter(redisTemplate, properties);
    }
}
