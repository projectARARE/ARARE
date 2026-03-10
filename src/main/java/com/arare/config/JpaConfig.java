package com.arare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables @CreatedDate and @LastModifiedDate population in BaseEntity.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
