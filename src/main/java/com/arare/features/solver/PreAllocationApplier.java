package com.arare.features.solver;

import com.arare.features.classsession.ClassSession;
import com.arare.features.preallocation.PreAllocation;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PreAllocationApplier {

    public void apply(List<ClassSession> sessions, List<PreAllocation> lockedPreAllocations) {
        for (PreAllocation pa : lockedPreAllocations) {
            sessions.stream()
                .filter(s -> !s.isLocked()
                    && s.getSubject().getId().equals(pa.getSubject().getId())
                    && s.getBatch() != null
                    && s.getBatch().getId().equals(pa.getBatch().getId()))
                .findFirst()
                .ifPresent(s -> {
                    s.setTeacher(pa.getTeacher());
                    s.setRoom(pa.getRoom());
                    s.setTimeslot(pa.getTimeslot());
                    s.setLocked(true);
                });
        }
    }
}
