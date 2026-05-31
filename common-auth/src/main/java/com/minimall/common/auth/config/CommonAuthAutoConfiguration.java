package com.minimall.common.auth.config;

import com.minimall.common.auth.jwt.JwtUtils;
import com.minimall.common.auth.web.InternalAuthFilter;
import com.minimall.common.auth.web.UserContextFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties({JwtProperties.class, InternalAuthProperties.class})
public class CommonAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "minimall.auth.jwt", name = "secret")
    public JwtUtils jwtUtils(JwtProperties jwtProperties) {
        return new JwtUtils(jwtProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public UserContextFilter userContextFilter(
            ObjectProvider<JwtUtils> jwtUtilsProvider, InternalAuthProperties internalAuthProperties) {
        return new UserContextFilter(jwtUtilsProvider.getIfAvailable(), internalAuthProperties.getSecret());
    }

    @Bean
    @ConditionalOnMissingBean
    public InternalAuthFilter internalAuthFilter(InternalAuthProperties internalAuthProperties) {
        return new InternalAuthFilter(internalAuthProperties.getSecret());
    }
}
