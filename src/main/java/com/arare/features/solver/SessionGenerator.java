package com.arare.features.solver;

import com.arare.features.batch.Batch;
import com.arare.features.classsection.ClassSection;
import com.arare.features.classsession.ClassSession;
import com.arare.features.room.Room;
import com.arare.features.schedule.Schedule;
import com.arare.features.subject.Subject;
import java.util.List;

public interface SessionGenerator {

    List<ClassSession> generate(
        Schedule schedule,
        List<Subject> subjects,
        List<Batch> batches,
        List<ClassSection> sections,
        List<Room> rooms
    );
}
