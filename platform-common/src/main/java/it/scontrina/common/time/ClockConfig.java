package it.scontrina.common.time;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Clock iniettabile: nei test si sostituisce con Clock.fixed(...) per rendere deterministici
 * date di business, scadenze delle abilitazioni e backoff dei retry.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
