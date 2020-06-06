package com.yx.distributed.locks.config;

import com.yx.distributed.locks.DistributedLockManager;
import com.yx.distributed.locks.redis.RedisSingleNodeLockManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

@Configuration
//@Import({RedisAutoConfiguration.class})
@AutoConfigureAfter({RedisAutoConfiguration.class})
@EnableConfigurationProperties({DistributedLocksProperties.class})
public class DistributedLockAutoConfiguration {

    @Value("${spring.application.name:}")
    private String applicationName;

    @Autowired
    private DistributedLocksProperties distributedProperties;

    @Bean
    @ConditionalOnMissingBean(DistributedLockManager.class)
    @ConditionalOnProperty(name = "fusion.distributed.lock", havingValue = "redis", matchIfMissing = true)
    public DistributedLockManager redisDistributedLockManager(RedisConnectionFactory connectionFactory) {
        String prefix = distributedProperties.getPrefix();
        RedisSingleNodeLockManager lockManager = new RedisSingleNodeLockManager(prefix, Duration.ofMillis(distributedProperties.getLeaseTimeMills()), connectionFactory);
        return lockManager;
    }
}