package com.minimall.gateway.config;

import com.minimall.gateway.web.GatewayCorsWebFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;

@Configuration
@EnableConfigurationProperties(GatewayCorsProperties.class)
public class GatewayCorsConfig {

    @Bean
    public WebFilter gatewayCorsWebFilter(GatewayCorsProperties properties) {
        return new GatewayCorsWebFilter(properties);
    }
}
