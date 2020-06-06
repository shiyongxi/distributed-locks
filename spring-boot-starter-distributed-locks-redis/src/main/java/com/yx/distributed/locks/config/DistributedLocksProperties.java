package com.yx.distributed.locks.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "spring.distributed.lock")
public class DistributedLocksProperties {
    /**
     * 锁的前缀，默认无
     */
    private String prefix;

    private long leaseTimeMills = 30 * 1000;

    private MonitorProperties monitor = new MonitorProperties();

    @Data
    public static class MonitorProperties {
        private boolean enabled = true;
    }
}