package com.arare.features.schedule;

import com.arare.common.BaseEntity;
import com.arare.common.enums.ScheduleScope;
import com.arare.common.enums.ScheduleStatus;
import com.arare.common.enums.SchoolDay;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

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

    // Home institute for an institute-scoped (INSTITUTE) schedule. Null for a
    // university-wide schedule; drives cross-schedule teacher-conflict scoping.
    @Column
    private Long instituteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_schedule_id")
    private Schedule parentSchedule;

    @Column
    private String score;

    @Column(columnDefinition = "TEXT")
    private String scoreExplanation;

// Whole days this schedule may NOT use, e.g. the college is closed on
// Saturdays or Sundays. Enforced as a solver HARD constraint
// ("Scheduled on a blocked day"). Global days-off belong in
// UniversityConfig.workingDays; this is per-schedule blocking.
@ElementCollection(targetClass = SchoolDay.class)
@CollectionTable(name = "schedule_blocked_days", joinColumns = @JoinColumn(name = "schedule_id"))
@Enumerated(EnumType.STRING)
@Column(name = "day")
@Builder.Default
private List<SchoolDay> blockedDays = new ArrayList<>();

@PrePersist
@PreUpdate
private void normalize() {
    if (name != null) {
        name = name.trim();
    }
    if (name == null || name.isEmpty()) {
        throw new IllegalStateException("Schedule name is required.");
    }
}
}
