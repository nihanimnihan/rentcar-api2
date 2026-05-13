package com.rentcar.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;

/**
 * Provides the application-wide {@link Clock} bean.
 *
 * Production: always UTC wall clock.
 * Tests: override by declaring {@code @Bean Clock clock() { return Clock.fixed(...); }}
 * in a {@code @TestConfiguration} — {@link ConditionalOnMissingBean} ensures the real bean
 * is skipped when a test bean is present.
 */
@Configuration
public class AppClockConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
