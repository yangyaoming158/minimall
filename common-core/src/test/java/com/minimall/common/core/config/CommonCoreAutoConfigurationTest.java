package com.minimall.common.core.config;

import com.minimall.common.core.audit.AdminAuditLogWriteRequest;
import com.minimall.common.core.audit.AdminAuditWriter;
import com.minimall.common.core.audit.JdbcAdminAuditWriter;
import com.minimall.common.core.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

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

    @Test
    void registersAdminAuditWriterWhenJdbcTemplateExists() {
        contextRunner
                .withBean(JdbcTemplate.class, () -> new JdbcTemplate(newDataSource()))
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(AdminAuditWriter.class).size());
                    assertInstanceOf(JdbcAdminAuditWriter.class, context.getBean(AdminAuditWriter.class));
                });
    }

    @Test
    void doesNotRegisterAdminAuditWriterWithoutJdbcTemplate() {
        contextRunner.run(context -> assertEquals(0, context.getBeansOfType(AdminAuditWriter.class).size()));
    }

    @Test
    void backsOffWhenCustomAdminAuditWriterExists() {
        CustomAdminAuditWriter customWriter = new CustomAdminAuditWriter();

        contextRunner
                .withBean(JdbcTemplate.class, () -> new JdbcTemplate(newDataSource()))
                .withBean(AdminAuditWriter.class, () -> customWriter)
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(AdminAuditWriter.class).size());
                    assertEquals(customWriter, context.getBean(AdminAuditWriter.class));
                });
    }

    private static DriverManagerDataSource newDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:common_core_auto_config;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static final class CustomGlobalExceptionHandler extends GlobalExceptionHandler {
    }

    private static final class CustomAdminAuditWriter implements AdminAuditWriter {

        @Override
        public void write(AdminAuditLogWriteRequest request) {
        }
    }
}
