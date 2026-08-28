package com.arare.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
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

    @Value("${spring.flyway.target:}")
    private String flywayTarget;

    @Value("${arare.flyway.auto-create-db:false}")
    private boolean autoCreateDb;

    // Custom Flyway bean that ensures the target database exists before migrating.
    // Connects to the default 'postgres' database first to CREATE DATABASE IF NOT EXISTS.
    @Bean
    public Flyway flyway(DataSource dataSource) {
        ensureDatabaseExists();

        FluentConfiguration configurer = Flyway.configure()
                .dataSource(dataSource)
                .locations(flywayLocations.split(","))
                .baselineOnMigrate(baselineOnMigrate);
        if (flywayTarget != null && !flywayTarget.isBlank()) {
            configurer.target(flywayTarget);
        }

        Flyway flyway = configurer.load();

        flyway.migrate();
        return flyway;
    }

    private void ensureDatabaseExists() {
        if (!autoCreateDb) {
            // Creating the DB is opt-in. By default the database must already
            // exist (see docs); if it does not, Flyway's connect will surface a
            // clear error below.
            return;
        }
        if (datasourceUrl != null && datasourceUrl.startsWith("jdbc:h2:")) {
            return;
        }
        String targetDb = extractDatabaseName(datasourceUrl);
        // Validate the extracted name to avoid injection-shaped concatenation.
        if (targetDb == null || !targetDb.matches("[A-Za-z0-9_]+")) {
            log.warn("Skipping auto-create-db: extracted database name '{}' is not valid.", targetDb);
            return;
        }
        String adminUrl = datasourceUrl.replace("/" + targetDb, "/postgres");

        try (Connection conn = DriverManager.getConnection(adminUrl, datasourceUsername, datasourcePassword);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE " + targetDb);
        } catch (Exception e) {
            // Database likely already exists; ignore and let Flyway handle the rest.
            if (!e.getMessage().contains("already exists") && !e.getMessage().contains("duplicate")) {
                log.warn("Could not ensure database exists (continuing): {}", e.getMessage());
            }
        }
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JpaConfig.class);

    private String extractDatabaseName(String url) {
        int lastSlash = url.lastIndexOf('/');
        int queryStart = url.indexOf('?', lastSlash);
        if (queryStart > 0) {
            return url.substring(lastSlash + 1, queryStart);
        }
        return url.substring(lastSlash + 1);
    }
}
