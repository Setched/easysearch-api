package me.setched.easysearch.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides a system {@link Clock} bean so time-dependent code can be injected with a clock instead of
 * calling static {@code now()} methods directly, making it substitutable in tests.
 */
@Configuration
public class ClockConfig {

    /**
     * @return a clock backed by the system default time zone, UTC
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
