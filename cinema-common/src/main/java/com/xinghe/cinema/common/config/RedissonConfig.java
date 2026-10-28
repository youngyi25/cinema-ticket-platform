package com.xinghe.cinema.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 客户端。使用 redisson 核心包手写装配，避免 starter 与 Spring Data Redis 版本矩阵耦合。
 * 连接参数复用 spring.data.redis.* 配置。
 */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties properties) {
        String host = StringUtils.hasText(properties.getHost()) ? properties.getHost() : "127.0.0.1";
        int port = properties.getPort() > 0 ? properties.getPort() : 6379;

        StringBuilder address = new StringBuilder("redis://");
        if (StringUtils.hasText(properties.getPassword())) {
            address.append(properties.getPassword()).append('@');
        }
        address.append(host).append(':').append(port);

        Config config = new Config();
        config.useSingleServer()
                .setAddress(address.toString())
                .setDatabase(properties.getDatabase())
                .setConnectTimeout(3000)
                .setTimeout(3000)
                .setConnectionPoolSize(32)
                .setConnectionMinimumIdleSize(4);
        return Redisson.create(config);
    }
}
