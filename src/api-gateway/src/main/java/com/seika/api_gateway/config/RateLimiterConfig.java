package com.seika.api_gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimiterConfig {

    @Primary
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isEmpty()) {
                return Mono.just(userId);
            }
            // Fallback to IP address if user is not authenticated
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            return Mono.just(remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown-ip");
        };
    }

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            return Mono.just(remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown-ip");
        };
    }
}
