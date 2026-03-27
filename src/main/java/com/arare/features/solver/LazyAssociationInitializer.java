package com.arare.features.solver;

import com.arare.features.batch.Batch;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsession.ClassSession;
import com.arare.features.room.Room;
import com.arare.features.subject.Subject;
import com.arare.features.teacher.Teacher;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LazyAssociationInitializer {

    public void initialize(
        List<ClassSession> sessions,
        List<Teacher> teachers,
        List<Room> rooms,
        List<Subject> subjects,
        List<Batch> batches,
        List<ClassSection> sections
    ) {
        for (ClassSession s : sessions) {
            Subject sub = s.getSubject();
            sub.getDepartment().getId();
            sub.getDepartment().getBuildingsAllowed().size();

            if (s.getBatch() != null) {
                Batch b = s.getBatch();
                b.getDepartment().getId();
                b.getWorkingDays().size();
            }
            if (s.getSection() != null) {
                s.getSection().getId();
                s.getSection().getBatch().getId();
            }
        }

        for (Teacher t : teachers) {
            t.getSubjects().size();
            t.getPreferredBuildings().size();
            t.getAvailableTimeslots().size();
        }

        for (Room r : rooms) {
            r.getBuilding().getId();
            r.getAvailableTimeslots().size();
        }

        for (Subject sub : subjects) {
            sub.getDepartment().getId();
            sub.getDepartment().getBuildingsAllowed().size();
        }

        for (Batch b : batches) {
            b.getDepartment().getId();
            b.getWorkingDays().size();
        }

        for (ClassSection sec : sections) {
            sec.getBatch().getId();
        }
    }
}
