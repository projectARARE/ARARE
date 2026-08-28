package com.arare.features.solver;

import com.arare.features.batch.Batch;
import com.arare.features.classsession.ClassSession;
import com.arare.features.teacherassignment.TeacherAssignment;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TimetableProblemBuilder {

    private final ProblemDataGateway dataGateway;
    private final SessionGenerator sessionGenerator;
    private final TimeslotTopologyValidator topologyValidator;
    private final ParentLockedSessionApplier parentLockedSessionApplier;
    private final PreAllocationApplier preAllocationApplier;
    private final LazyAssociationInitializer lazyAssociationInitializer;

    public TimetableSolution build(ProblemBuildRequest request) {
        return build(request, true);
    }

    /**
     * Builds the solver problem. When {@code generateIfMissing} is false (the
     * read-only explain path), no sessions are generated nor persisted: a
     * schedule that has no sessions yet simply yields an empty session set for
     * analysis, so {@code explainSchedule} never issues a write inside its
     * read-only transaction.
     */
    public TimetableSolution build(ProblemBuildRequest request, boolean generateIfMissing) {
        ProblemFacts facts = dataGateway.loadFacts(request);
        topologyValidator.validate(facts.timeslots(), facts.configs());

        List<ClassSession> sessions = getOrGenerateSessions(request, facts, generateIfMissing);

        if (request.schedule().getParentSchedule() != null) {
            List<ClassSession> parentLocked = dataGateway.findLockedParentSessions(
                request.schedule().getParentSchedule().getId());
            parentLockedSessionApplier.apply(request.schedule(), sessions, parentLocked);
        }

        // Locked pre-allocations: a timeslot-less pre-allocation pins teacher
        // (and optionally room) while leaving the slot to the solver. Sessions
        // in the impacted set are exempt: a disruption that targets a
        // pre-allocated session (e.g. teacher unavailable) must be allowed to
        // move it, otherwise the repair is unsolvable by construction.
        List<Long> impacted = request.impactedSessionIds();
        List<PreAllocationConstraintFact> preAllocationFacts = preAllocationApplier.apply(
            sessions,
            dataGateway.findLockedPreAllocations(request.schedule().getId()),
            impacted != null ? impacted : List.of()
        );

        if (impacted != null) {
            sessions.forEach(s -> {
                if (impacted.contains(s.getId())) {
                    // Impacted sessions MUST be movable in this re-solve. A
                    // prior partial resolve persists solver-induced locks, so a
                    // session that was stable last time can arrive already
                    // locked; leaving it pinned would make the re-solve
                    // impossible whenever the new disruption fact targets it.
                    s.setLocked(false);
                } else {
                    s.setLocked(true);
                }
            });
        }

        lazyAssociationInitializer.initialize(
            sessions,
            facts.teachers(),
            facts.rooms(),
            facts.subjects(),
            facts.batches(),
            facts.sections()
        );

        applyTeacherAllotments(sessions, facts);

        TimetableSolution problem = new TimetableSolution();
        problem.setTimeslots(facts.timeslots());
        problem.setRooms(facts.rooms());
        problem.setTeachers(facts.teachers());
        problem.setSubjects(facts.subjects());
        problem.setBatches(facts.batches());
        problem.setClassSections(facts.sections());
        problem.setBuildings(facts.buildings());
        problem.setConfigs(facts.configs());
        problem.setSessions(sessions);
        problem.setPreAllocationFacts(preAllocationFacts);
        problem.setTeacherBusyIntervals(dataGateway.findTeacherBusyIntervals(
            request.schedule().getId(),
            request.schedule().getInstituteId(),
            facts.teachers().stream().map(t -> t.getId()).toList()
        ));
        problem.setPreviousAssignments(
            request.schedule().getParentSchedule() != null
                ? dataGateway.findPreviousAssignments(request.schedule().getParentSchedule().getId())
                : buildChurnBaseline(sessions, request.impactedSessionIds())
        );
        problem.setDisruptionFacts(request.disruptionFacts());
        return problem;
    }

    private List<ClassSession> getOrGenerateSessions(ProblemBuildRequest request, ProblemFacts facts, boolean generateIfMissing) {
        List<ClassSession> existing = dataGateway.findSessionsByScheduleId(request.schedule().getId());
        if (!existing.isEmpty()) {
            return existing;
        }
        if (!generateIfMissing) {
            // Read-only explain path: never persist generated sessions.
            return List.of();
        }

        List<ClassSession> generated = sessionGenerator.generate(
            request.schedule(),
            facts.subjects(),
            facts.batches(),
            facts.sections(),
            facts.rooms()
        );
        return dataGateway.saveSessions(generated);
    }

    /**
     * On a partial resolve (disruption repair), snapshots the schedule's CURRENT
     * session assignments as {@link PreviousAssignment} facts. There is no parent
     * schedule to read from, so without this the {@code minimizeMovedSessions}
     * soft constraint would never fire and the solver could freely reshuffle the
     * teacher/room/timeslot of every impacted session. Feeding the pre-repair
     * state back in makes the re-solve prefer to keep sessions exactly where they
     * are, only moving what the disruption forces — the room/teacher churn
     * priority pass.
     */
    private List<PreviousAssignment> buildChurnBaseline(List<ClassSession> sessions, List<Long> impactedSessionIds) {
        if (impactedSessionIds == null || impactedSessionIds.isEmpty()) {
            return List.of();
        }
        return sessions.stream()
            .filter(cs -> cs.getSubject() != null
                && cs.getTeacher() != null
                && cs.getRoom() != null
                && cs.getTimeslot() != null)
            .map(cs -> new PreviousAssignment(
                PreviousAssignment.keyFor(cs),
                cs.getTeacher().getId(),
                cs.getRoom().getId(),
                cs.getTimeslot().getId()))
            .toList();
    }

    /**
     * Derives each session's {@code allowedTeacherIds} from the term teacher
     * allotments in scope. A class with an allotment may only be taught by the
     * allotted teacher (HARD constraint); a class without one keeps the
     * qualified-teacher fallback. Section-level allotments (lab splits) take
     * precedence over batch-level ones for the same subject.
     */
    private void applyTeacherAllotments(List<ClassSession> sessions, ProblemFacts facts) {
        List<TeacherAssignment> assignments = dataGateway.loadAssignments(
            facts.batches().stream().map(b -> b.getId()).toList(),
            facts.sections().stream().map(s -> s.getId()).toList()
        );
        if (assignments.isEmpty()) {
            return;
        }

        for (ClassSession session : sessions) {
            session.setAllowedTeacherIds(resolveAllowedTeacherIds(session, assignments));
        }
    }

    private List<Long> resolveAllowedTeacherIds(ClassSession session, List<TeacherAssignment> assignments) {
        Long subjectId = session.getSubject().getId();
        Batch effectiveBatch = session.getEffectiveBatch();
        if (effectiveBatch == null) {
            return null;
        }

        if (session.getSection() != null) {
            Long sectionId = session.getSection().getId();
            List<Long> sectionTeachers = assignments.stream()
                .filter(a -> a.getSubject().getId().equals(subjectId)
                    && a.getSection() != null
                    && a.getSection().getId().equals(sectionId))
                .map(a -> a.getTeacher().getId())
                .distinct()
                .toList();
            if (!sectionTeachers.isEmpty()) {
                return sectionTeachers;
            }
        }

        Long batchId = effectiveBatch.getId();
        List<Long> batchTeachers = assignments.stream()
            .filter(a -> a.getSubject().getId().equals(subjectId)
                && a.getSection() == null
                && a.getBatch().getId().equals(batchId))
            .map(a -> a.getTeacher().getId())
            .distinct()
            .toList();
        return batchTeachers.isEmpty() ? null : batchTeachers;
    }
}
