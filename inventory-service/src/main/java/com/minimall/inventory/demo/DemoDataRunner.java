package com.minimall.inventory.demo;

import com.minimall.inventory.config.DemoDataProperties;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class DemoDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataRunner.class);
    private static final Set<String> ALLOWED_PROFILES = Set.of("dev", "test");
    private static final Set<String> FORBIDDEN_PROFILES = Set.of("prod", "production");

    private final DemoDataProperties properties;
    private final Environment environment;
    private final List<DemoDataSeedContributor> contributors;

    public DemoDataRunner(
            DemoDataProperties properties,
            Environment environment,
            List<DemoDataSeedContributor> contributors) {
        this.properties = properties;
        this.environment = environment;
        this.contributors = contributors;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.debug("Demo data generator is disabled");
            return;
        }

        validateProfiles();
        if (contributors.isEmpty()) {
            log.info("Demo data generator is enabled with no seed contributors");
            return;
        }

        contributors.forEach(DemoDataSeedContributor::seed);
        log.info("Demo data generator completed {} seed contributor(s)", contributors.size());
    }

    private void validateProfiles() {
        Set<String> activeProfiles = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        if (activeProfiles.stream().anyMatch(FORBIDDEN_PROFILES::contains)) {
            throw new IllegalStateException("Demo data generator must not run with production profile");
        }
        if (activeProfiles.stream().noneMatch(ALLOWED_PROFILES::contains)) {
            throw new IllegalStateException("Demo data generator requires dev or test profile");
        }
    }
}
