package com.minimall.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "minimall.order.timeout")
public class OrderTimeoutProperties {

    private boolean enabled = true;
    private long fixedDelay = 60_000L;
    private int batchSize = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getFixedDelay() {
        return fixedDelay;
    }

    public void setFixedDelay(long fixedDelay) {
        this.fixedDelay = atLeastOne(fixedDelay);
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = (int) atLeastOne(batchSize);
    }

    private long atLeastOne(long value) {
        return Math.max(1L, value);
    }
}
