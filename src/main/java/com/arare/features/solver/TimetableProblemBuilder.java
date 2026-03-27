package com.arare.features.solver;

import com.arare.features.classsession.ClassSession;
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
        ProblemFacts facts = dataGateway.loadFacts(request);
        topologyValidator.validate(facts.timeslots(), facts.configs());

        List<ClassSession> sessions = getOrGenerateSessions(request, facts);

        if (request.schedule().getParentSchedule() != null) {
            List<ClassSession> parentLocked = dataGateway.findLockedParentSessions(
                request.schedule().getParentSchedule().getId());
            parentLockedSessionApplier.apply(request.schedule(), sessions, parentLocked);
        }

        preAllocationApplier.apply(
            sessions,
            dataGateway.findLockedPreAllocations(request.schedule().getId())
        );

        if (request.impactedSessionIds() != null) {
            sessions.forEach(s -> {
                if (!request.impactedSessionIds().contains(s.getId())) {
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
        return problem;
    }

    private List<ClassSession> getOrGenerateSessions(ProblemBuildRequest request, ProblemFacts facts) {
        List<ClassSession> existing = dataGateway.findSessionsByScheduleId(request.schedule().getId());
        if (!existing.isEmpty()) {
            return existing;
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
}
