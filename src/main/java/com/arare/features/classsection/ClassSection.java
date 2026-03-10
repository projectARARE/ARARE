package com.arare.features.classsection;

import com.arare.common.BaseEntity;
import com.arare.features.batch.Batch;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * A sub-division of a {@link Batch} for labs where room capacity is smaller
 * than the full batch size.
 *
 * <p>Example:
 * <pre>
 *   Batch CSE-2A: 60 students
 *   Lab capacity: 36
 *
 *   Section A: 30 students  → assigned to Lab slot 1
 *   Section B: 30 students  → assigned to Lab slot 2
 * </pre>
 * </p>
 *
 * <p>Each ClassSection generates its own {@link com.arare.features.classsession.ClassSession}
 * for lab subjects, enabling independent room and timeslot assignment.</p>
 */
@Entity
@Table(name = "class_sections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassSection extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    /** Label for the section, e.g. "A", "B". */
    @Column(nullable = false)
    @Builder.Default
    private String label = "A";

    @Min(1)
    @Column(nullable = false)
    private int size;
}
