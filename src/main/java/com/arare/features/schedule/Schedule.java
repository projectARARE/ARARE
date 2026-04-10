package com.arare.features.schedule;

import com.arare.common.BaseEntity;
import com.arare.common.enums.ScheduleScope;
import com.arare.common.enums.ScheduleStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_schedule_id")
    private Schedule parentSchedule;

    @Column
    private String score;

    @Column(columnDefinition = "TEXT")
    private String scoreExplanation;

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
