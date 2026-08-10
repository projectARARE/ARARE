package com.arare.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

// Enables @CreatedDate and @LastModifiedDate population in BaseEntity.
@Configuration
@EnableJpaAuditing
public class JpaConfig {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String flywayLocations;

    @Value("${spring.flyway.baseline-on-migrate:true}")
    private boolean baselineOnMigrate;

    // Custom Flyway bean that ensures the target database exists before migrating.
    // Connects to the default 'postgres' database first to CREATE DATABASE IF NOT EXISTS.
    @Bean
    public Flyway flyway(DataSource dataSource) {
        ensureDatabaseExists();

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(flywayLocations.split(","))
                .baselineOnMigrate(baselineOnMigrate)
                .load();

        flyway.migrate();
        return flyway;
    }

    private void ensureDatabaseExists() {
        String targetDb = extractDatabaseName(datasourceUrl);
        String adminUrl = datasourceUrl.replace("/" + targetDb, "/postgres");

        try (Connection conn = DriverManager.getConnection(adminUrl, datasourceUsername, datasourcePassword);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE " + targetDb);
        } catch (Exception e) {
            // Database likely already exists; ignore and let Flyway handle the rest.
            if (!e.getMessage().contains("already exists") && !e.getMessage().contains("duplicate")) {
                throw new IllegalStateException("Failed to ensure database exists: " + e.getMessage(), e);
            }
        }
    }

    private String extractDatabaseName(String url) {
        int lastSlash = url.lastIndexOf('/');
        int queryStart = url.indexOf('?', lastSlash);
        if (queryStart > 0) {
            return url.substring(lastSlash + 1, queryStart);
        }
        return url.substring(lastSlash + 1);
    }
}
