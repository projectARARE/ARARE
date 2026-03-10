package com.arare.features.preallocation;

import com.arare.common.BaseEntity;
import com.arare.features.batch.Batch;
import com.arare.features.room.Room;
import com.arare.features.schedule.Schedule;
import com.arare.features.subject.Subject;
import com.arare.features.teacher.Teacher;
import com.arare.features.timeslot.Timeslot;
import jakarta.persistence.*;
import lombok.*;

/**
 * A manually fixed (pre-allocated) assignment that the solver must honour.
 *
 * <p>Before the solver runs, SolverService iterates all PreAllocations,
 * finds the corresponding {@link com.arare.features.classsession.ClassSession},
 * sets its teacher/room/timeslot fields, and marks it as {@code isLocked = true}.</p>
 *
 * <p>Example use case:<br>
 * "Prof. Sharma MUST teach DSA for CSE-2A on Monday at 9:00 AM in Room 101."</p>
 */
@Entity
@Table(name = "pre_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    /** Fixed teacher; null if subject does not require one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private Teacher teacher;

    /** Fixed room; null if subject does not require one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timeslot_id", nullable = false)
    private Timeslot timeslot;

    /**
     * When true, the solver cannot override this assignment.
     * When false, the allocation is treated as a strong preference (soft/medium constraint).
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean locked = true;
}
