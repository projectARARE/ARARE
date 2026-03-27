package com.arare.features.solver;

import com.arare.features.batch.Batch;
import com.arare.features.building.Building;
import com.arare.features.classsection.ClassSection;
import com.arare.features.room.Room;
import com.arare.features.subject.Subject;
import com.arare.features.teacher.Teacher;
import com.arare.features.timeslot.Timeslot;
import com.arare.features.universityconfig.UniversityConfig;
import java.util.List;

public record ProblemFacts(
    List<Timeslot> timeslots,
    List<Building> buildings,
    List<UniversityConfig> configs,
    List<Room> rooms,
    List<Teacher> teachers,
    List<Subject> subjects,
    List<Batch> batches,
    List<ClassSection> sections
) {}
