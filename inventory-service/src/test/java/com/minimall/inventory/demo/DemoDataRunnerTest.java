package com.minimall.inventory.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minimall.inventory.config.DemoDataProperties;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

class DemoDataRunnerTest {

    @Test
    void disabledRunnerDoesNotSeedEvenWithProdProfile() throws Exception {
        AtomicInteger seedCount = new AtomicInteger();
        DemoDataProperties properties = properties(false);
        MockEnvironment environment = environment("prod");

        runner(properties, environment, seedCount).run(new DefaultApplicationArguments());

        assertThat(seedCount).hasValue(0);
    }

    @Test
    void enabledRunnerRunsContributorsWithDevProfile() throws Exception {
        AtomicInteger seedCount = new AtomicInteger();
        DemoDataProperties properties = properties(true);
        MockEnvironment environment = environment("dev");

        runner(properties, environment, seedCount).run(new DefaultApplicationArguments());

        assertThat(seedCount).hasValue(1);
    }

    @Test
    void enabledRunnerRunsContributorsWithTestProfile() throws Exception {
        AtomicInteger seedCount = new AtomicInteger();
        DemoDataProperties properties = properties(true);
        MockEnvironment environment = environment("test");

        runner(properties, environment, seedCount).run(new DefaultApplicationArguments());

        assertThat(seedCount).hasValue(1);
    }

    @Test
    void enabledRunnerRejectsMissingAllowedProfile() {
        AtomicInteger seedCount = new AtomicInteger();
        DemoDataProperties properties = properties(true);
        MockEnvironment environment = environment();

        assertThatThrownBy(() -> runner(properties, environment, seedCount).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Demo data generator requires dev or test profile");
        assertThat(seedCount).hasValue(0);
    }

    @Test
    void enabledRunnerRejectsProductionProfile() {
        AtomicInteger seedCount = new AtomicInteger();
        DemoDataProperties properties = properties(true);
        MockEnvironment environment = environment("dev", "prod");

        assertThatThrownBy(() -> runner(properties, environment, seedCount).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Demo data generator must not run with production profile");
        assertThat(seedCount).hasValue(0);
    }

    private DemoDataRunner runner(
            DemoDataProperties properties,
            MockEnvironment environment,
            AtomicInteger seedCount) {
        return new DemoDataRunner(properties, environment, List.of(seedCount::incrementAndGet));
    }

    private DemoDataProperties properties(boolean enabled) {
        DemoDataProperties properties = new DemoDataProperties();
        properties.setEnabled(enabled);
        return properties;
    }

    private MockEnvironment environment(String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return environment;
    }
}
