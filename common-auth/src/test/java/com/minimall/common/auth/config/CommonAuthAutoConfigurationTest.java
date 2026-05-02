package com.minimall.common.auth.config;

import com.minimall.common.auth.jwt.JwtUtils;
import com.minimall.common.auth.web.UserContextFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class CommonAuthAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonAuthAutoConfiguration.class));

    @Test
    void registersJwtUtilsAndUserContextFilterWhenJwtSecretIsConfigured() {
        contextRunner
                .withPropertyValues(
                        "minimall.auth.jwt.secret=test-secret-with-enough-entropy-for-hmac",
                        "minimall.auth.jwt.expire-seconds=120")
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtProperties.class);
                    assertThat(context).hasSingleBean(JwtUtils.class);
                    assertThat(context).hasSingleBean(UserContextFilter.class);
                    assertThat(context.getBean(JwtProperties.class).getExpireSeconds()).isEqualTo(120);
                });
    }

    @Test
    void registersUserContextFilterWithoutJwtUtilsWhenJwtSecretIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JwtProperties.class);
            assertThat(context).doesNotHaveBean(JwtUtils.class);
            assertThat(context).hasSingleBean(UserContextFilter.class);
        });
    }

    @Test
    void backsOffWhenCustomFilterIsProvided() {
        contextRunner
                .withUserConfiguration(CustomFilterConfiguration.class)
                .withPropertyValues("minimall.auth.jwt.secret=test-secret-with-enough-entropy-for-hmac")
                .run(context -> {
                    assertThat(context).hasSingleBean(UserContextFilter.class);
                    assertThat(context.getBean(UserContextFilter.class))
                            .isSameAs(context.getBean(CustomFilterConfiguration.CUSTOM_FILTER_NAME));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomFilterConfiguration {

        static final String CUSTOM_FILTER_NAME = "customUserContextFilter";

        @Bean(CUSTOM_FILTER_NAME)
        UserContextFilter customUserContextFilter() {
            return new UserContextFilter(null);
        }
    }
}
