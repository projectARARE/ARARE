package com.arare.features.solver;

import com.arare.features.classsession.ClassSession;
import com.arare.features.preallocation.PreAllocation;
import java.util.List;

public interface ProblemDataGateway {

    ProblemFacts loadFacts(ProblemBuildRequest request);

    List<ClassSession> findSessionsByScheduleId(Long scheduleId);

    List<ClassSession> saveSessions(List<ClassSession> sessions);

    List<ClassSession> findLockedParentSessions(Long parentScheduleId);

    List<PreAllocation> findLockedPreAllocations(Long scheduleId);
}
