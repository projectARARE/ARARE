package com.arare.features.subject;

import com.arare.common.BaseEntity;
import com.arare.common.enums.LabSubtype;
import com.arare.common.enums.RoomType;
import com.arare.features.department.Department;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * An academic subject (course component).
 *
 * <p>Lectures and labs for the same course are modelled as <b>separate Subject records</b>
 * to allow independent room-type and teacher assignment. For example:
 * <ul>
 *   <li>DSA Lecture  – roomTypeRequired=LECTURE, isLab=false</li>
 *   <li>DSA Lab      – roomTypeRequired=LAB, isLab=true</li>
 * </ul>
 * </p>
 *
 * <p>Key scheduling fields used by the constraint provider:
 * <ul>
 *   <li>{@code weeklyHours}         – total hours per week → determines how many ClassSessions to generate</li>
 *   <li>{@code chunkHours}          – duration of one session (e.g. 1 for lecture, 2 for lab)</li>
 *   <li>{@code maxSessionsPerDay}   – soft/medium cap; default 1</li>
 *   <li>{@code minGapBetweenSess}   – minimum timeslot gap between two sessions of same subject</li>
 *   <li>{@code requiresTeacher}     – false for self-study / project sessions</li>
 *   <li>{@code requiresRoom}        – false for field work / off-campus activities</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "subjects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subject extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    /** Total contact hours per week (e.g. 4). */
    @Min(1)
    @Column(nullable = false)
    private int weeklyHours;

    /**
     * Duration of one session in hours (e.g. 1 for lecture, 2 or 3 for lab).
     * weeklyHours / chunkHours = number of ClassSessions to create per week.
     */
    @Min(1)
    @Column(nullable = false)
    @Builder.Default
    private int chunkHours = 1;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RoomType roomTypeRequired = RoomType.LECTURE;

    /** Required lab subtype; null for lecture subjects. */
    @Enumerated(EnumType.STRING)
    @Column
    private LabSubtype labSubtypeRequired;

    @Column(nullable = false)
    @Builder.Default
    private boolean isLab = false;

    /**
     * When false the solver will not assign a teacher (self-study, project, seminar).
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean requiresTeacher = true;

    /**
     * When false the solver will not assign a room (field work, off-campus).
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean requiresRoom = true;

    /**
     * Minimum number of timeslots between two sessions of this subject.
     * Supports the "spread sessions across the week" soft constraint.
     */
    @Min(0)
    @Column(nullable = false)
    @Builder.Default
    private int minGapBetweenSessions = 0;

    /**
     * Soft/medium constraint: max sessions of this subject per day.
     * Default 1 implements the cognitive-load constraint.
     */
    @Min(1)
    @Column(nullable = false)
    @Builder.Default
    private int maxSessionsPerDay = 1;
}
