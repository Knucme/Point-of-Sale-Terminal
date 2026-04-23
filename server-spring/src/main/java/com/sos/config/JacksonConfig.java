package com.sos.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Jackson to handle Hibernate lazy proxies properly.
 * Without this, serializing a lazy-loaded entity (e.g., MenuItem.inventoryItem)
 * throws an error on the internal hibernateLazyInitializer field.
 *
 * FORCE_LAZY_LOADING = true ensures proxies are initialized during serialization
 * (safe because Spring Boot's open-in-view keeps the session open).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Hibernate6Module hibernate6Module() {
        Hibernate6Module module = new Hibernate6Module();
        module.configure(Hibernate6Module.Feature.FORCE_LAZY_LOADING, true);
        return module;
    }
}
