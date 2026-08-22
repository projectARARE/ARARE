package com.arare.features.subjectoffering;

import com.arare.common.BaseEntity;
import com.arare.features.batch.Batch;
import com.arare.features.classsection.ClassSection;
import com.arare.features.subject.Subject;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

// The "offered this term" layer of the curriculum: which batches or sections
// actually take which subject this academic term.
// <p>This is the load-bearing fix for shared, elective and institute-wide
// subjects. The catalogue (Subject) is intentionally decoupled from the
// offering so that ONE subject row can serve MANY batches/sections, including
// across departments and institutes:
// <ul>
// <li><b>Shared / mega lectures</b> — a single "Engineering Mathematics"
// subject is offered to every first-year batch (one SubjectOffering row per
// batch).</li>
// <li><b>Electives</b> — a subject marked {@code elective} is attached only to
// the batches that selected it.</li>
// <li><b>Institute-wide subjects</b> — a Subject with a {@code null}
// department (created via the institute-wide flag on the subject form) can be
// offered to batches of ANY institute.</li>
// </ul>
// </p>
// <p>An offering must reference exactly one of:
// <ul>
// <li>{@code batch}   — the whole batch takes the subject as one class;</li>
// <li>{@code section} — a lab split where each section is taught separately.</li>
// </ul>
// A per-offering {@code weeklyHours} may override the catalogue load (e.g. an
// institute-wide subject taught 2h in one college and 4h in another).
// </p>
// <p>Backward compatibility: when a batch/section has NO offerings the
// session generator falls back to the legacy {@code batch.subjects} /
// {@code section.subjects} join tables (empty = inherit all), so existing data
// keeps scheduling exactly as before.</p>
@Entity
@Table(name = "subject_offerings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubjectOffering extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    // Offering scope: exactly one of batch / section must be set.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private ClassSection section;

    // Optional per-offering load override. When null the catalogue
    // subject.weeklyHours drives the generated session count.
    @Min(1)
    @Column(name = "weekly_hours")
    private Integer weeklyHours;

    // True when this is an elective / choice-based subject offered to a
    // subset of batches rather than the default curriculum.
    @Column(nullable = false)
    @Builder.Default
    private boolean elective = false;

    @PrePersist
    @PreUpdate
    private void validateInvariant() {
        if (batch == null && section == null) {
            throw new IllegalStateException(
                "SubjectOffering must reference either a batch or a section.");
        }
        if (batch != null && section != null
            && section.getBatch() != null
            && !section.getBatch().getId().equals(batch.getId())) {
            throw new IllegalStateException(
                "SubjectOffering section " + section.getLabel()
                    + " does not belong to batch " + batch.getSection() + ".");
        }
        if (weeklyHours != null && weeklyHours <= 0) {
            throw new IllegalStateException("SubjectOffering weeklyHours must be > 0.");
        }
    }
}
