package com.minimall.common.core.config;

import com.minimall.common.core.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CommonCoreAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonCoreAutoConfiguration.class));

    @Test
    void registersGlobalExceptionHandler() {
        contextRunner.run(context -> assertEquals(1, context.getBeansOfType(GlobalExceptionHandler.class).size()));
    }

    @Test
    void backsOffWhenCustomGlobalExceptionHandlerExists() {
        contextRunner
                .withBean(GlobalExceptionHandler.class, CustomGlobalExceptionHandler::new)
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(GlobalExceptionHandler.class).size());
                    assertInstanceOf(CustomGlobalExceptionHandler.class, context.getBean(GlobalExceptionHandler.class));
                });
    }

    private static final class CustomGlobalExceptionHandler extends GlobalExceptionHandler {
    }
}
