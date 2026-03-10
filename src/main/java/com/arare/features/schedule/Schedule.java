package com.arare.features.schedule;

import com.arare.common.BaseEntity;
import com.arare.common.enums.ScheduleScope;
import com.arare.common.enums.ScheduleStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Represents one version of a generated timetable.
 *
 * <p>Every run of the solver (full or partial) creates a new Schedule.
 * Previous versions are retained for comparison and rollback.</p>
 *
 * <p>The parent-child relationship ({@code parentScheduleId}) links
 * a re-optimization result back to the schedule that triggered it,
 * enabling a full version history tree.</p>
 */
@Entity
@Table(name = "schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ScheduleScope scope = ScheduleScope.DEPARTMENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ScheduleStatus status = ScheduleStatus.DRAFT;

    /**
     * Reference to the schedule this was derived from (partial re-optimization).
     * Null for the first generated schedule.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_schedule_id")
    private Schedule parentSchedule;

    /**
     * Timefold score string serialised as text.
     * Format: "0hard/0medium/0soft" representing HardMediumSoftScore.
     * Stored for quick display; parse with ScoreManager when needed.
     */
    @Column
    private String score;

    /**
     * Human-readable explanation of any unresolved constraint violations,
     * produced by the solver's score explanation API.
     */
    @Column(columnDefinition = "TEXT")
    private String scoreExplanation;
}
