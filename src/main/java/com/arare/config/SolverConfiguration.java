package com.arare.config;

import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.arare.features.classsession.ClassSession;
import com.arare.features.solver.TimetableConstraintProvider;
import com.arare.features.solver.TimetableSolution;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * Configures the Timefold solver bean.
 *
 * <p>The {@code timefold.solver.termination.spent-limit} property in
 * {@code application.properties} controls solve time per request.
 * Override here if you need code-level control (e.g., different limits per scope).</p>
 *
 * <p>Spring Boot auto-configuration will also pick up the {@code @PlanningSolution}
 * and {@code @PlanningEntity} classes automatically via classpath scanning,
 * so this bean is only needed if you want to customise the SolverConfig programmatically.</p>
 */
@Configuration
public class SolverConfiguration {

    /**
     * Explicit SolverManager bean with custom termination.
     * Remove this bean to fall back to the auto-configured one from application.properties.
     */
    @Bean
    public SolverManager<TimetableSolution, UUID> solverManager() {
        SolverConfig config = new SolverConfig()
            .withSolutionClass(TimetableSolution.class)
            .withEntityClasses(ClassSession.class)
            .withConstraintProviderClass(TimetableConstraintProvider.class)
            .withTerminationConfig(new TerminationConfig()
                .withSpentLimit(java.time.Duration.ofSeconds(30)));

        return SolverManager.create(config);
    }
}
