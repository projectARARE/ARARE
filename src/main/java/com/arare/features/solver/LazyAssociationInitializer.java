package com.arare.features.solver;

import com.arare.features.batch.Batch;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsession.ClassSession;
import com.arare.features.room.Room;
import com.arare.features.subject.Subject;
import com.arare.features.teacher.Teacher;
import java.util.List;
import org.springframework.stereotype.Component;

//noinspection UnusedSince
@Component
public class LazyAssociationInitializer {

    /**
     * Force-load lazy associations before constraint traversal in the solver.
     * <p>
     * This is needed because some @ManyToMany collections use @Fetch(Lazy) instead
     * of @Fetch(SUBSELECT), which can cause LazyInitializationException during
     * constraint evaluation if the Hibernate session closes prematurely.
     * <p>
     * TODO: Once fetch strategies are standardized across all entities (using
     * @Fetch(SUBSELECT) on all ManyToMany collections), this class can be removed
     * and the @Fetch annotations can be left in place.
     */
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
            if (sub.getDepartment() != null) {
                sub.getDepartment().getId();
                sub.getDepartment().getBuildingsAllowed().size();
            }
            if (s.getSchedule() != null) {
                s.getSchedule().getBlockedDays().size();
            }

            if (s.getBatch() != null) {
                Batch b = s.getBatch();
                b.getDepartment().getId();
                b.getWorkingDays().size();
                b.getHomeRoom();
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
            if (r.getBuilding() != null) {
                r.getBuilding().getId();
            }
            r.getAvailableTimeslots().size();
        }

        for (Subject sub : subjects) {
            if (sub.getDepartment() != null) {
                sub.getDepartment().getId();
                sub.getDepartment().getBuildingsAllowed().size();
            }
        }

        for (Batch b : batches) {
            b.getDepartment().getId();
            b.getWorkingDays().size();
            b.getHomeRoom();
            b.getSubjects().size();
        }

        for (ClassSection sec : sections) {
            sec.getBatch().getId();
            sec.getSubjects().size();
        }
    }
}
