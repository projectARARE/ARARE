package com.arare.features.solver;

import com.arare.features.classsession.ClassSession;
import com.arare.features.schedule.Schedule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ParentLockedSessionApplier {

    public void apply(Schedule schedule, List<ClassSession> childSessions, List<ClassSession> parentLockedSessions) {
        if (schedule.getParentSchedule() == null || parentLockedSessions.isEmpty()) {
            return;
        }

        Map<String, List<ClassSession>> childByKey = new HashMap<>();
        for (ClassSession child : childSessions) {
            childByKey.computeIfAbsent(sessionMatchKey(child), k -> new ArrayList<>()).add(child);
        }
        childByKey.values().forEach(list -> list.sort(Comparator.comparing(ClassSession::getId)));

        Map<String, Integer> idxByKey = new HashMap<>();
        for (ClassSession parent : parentLockedSessions) {
            String key = sessionMatchKey(parent);
            List<ClassSession> candidates = childByKey.get(key);
            if (candidates == null || candidates.isEmpty()) {
                continue;
            }

            int idx = idxByKey.getOrDefault(key, 0);
            if (idx >= candidates.size()) {
                continue;
            }

            ClassSession child = candidates.get(idx);
            idxByKey.put(key, idx + 1);

            child.setTeacher(parent.getTeacher());
            child.setRoom(parent.getRoom());
            child.setTimeslot(parent.getTimeslot());
            child.setLocked(true);
        }
    }

    private String sessionMatchKey(ClassSession s) {
        Long subjectId = s.getSubject() != null ? s.getSubject().getId() : null;
        Long batchId = s.getBatch() != null
            ? s.getBatch().getId()
            : (s.getSection() != null && s.getSection().getBatch() != null ? s.getSection().getBatch().getId() : null);
        Long sectionId = s.getSection() != null ? s.getSection().getId() : null;
        return Objects.toString(subjectId, "null")
            + ":" + Objects.toString(batchId, "null")
            + ":" + Objects.toString(sectionId, "null")
            + ":" + s.getDuration();
    }
}
