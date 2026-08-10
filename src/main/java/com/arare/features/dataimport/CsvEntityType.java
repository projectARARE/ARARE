package com.arare.features.dataimport;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The importable entity kinds, each with its canonical file name and the
 * natural-key dependencies that must be satisfied before it can be imported.
 *
 * <p>Dependencies drive two behaviours:
 * <ul>
 *   <li>ZIP import processes files in dependency-ordered sequence.</li>
 *   <li>Single-entity import validates that referenced entities already exist
 *       (or are created in the same file) before committing rows.</li>
 * </ul>
 */
public enum CsvEntityType {

    TIMESLOTS("timeslots", "timeslots.csv", "Timeslots", Set.of()),
    BUILDINGS("buildings", "buildings.csv", "Buildings", Set.of()),
    DEPARTMENTS("departments", "departments.csv", "Departments", Set.of(BUILDINGS)),
    ROOMS("rooms", "rooms.csv", "Rooms", Set.of(BUILDINGS)),
    SUBJECTS("subjects", "subjects.csv", "Subjects", Set.of(DEPARTMENTS)),
    TEACHERS("teachers", "teachers.csv", "Teachers", Set.of(DEPARTMENTS, SUBJECTS, TIMESLOTS, BUILDINGS)),
    BATCHES("batches", "batches.csv", "Batches", Set.of(DEPARTMENTS));

    private final String name;
    private final String fileName;
    private final String displayName;
    private final Set<CsvEntityType> dependencies;

    CsvEntityType(String name, String fileName, String displayName, Set<CsvEntityType> dependencies) {
        this.name = name;
        this.fileName = fileName;
        this.displayName = displayName;
        this.dependencies = dependencies;
    }

    public String getName() {
        return name;
    }

    public String getFileName() {
        return fileName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<CsvEntityType> getDependencies() {
        return dependencies;
    }

    /** Resolves an entity type by route name, case-insensitively. */
    public static CsvEntityType fromName(String raw) {
        String normalized = CsvUtils.key(raw);
        for (CsvEntityType type : values()) {
            if (type.name.equalsIgnoreCase(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException(
            "Unsupported entity type '" + raw + "'. Supported: "
                + String.join(", ", Arrays.stream(values()).map(CsvEntityType::getName).toList())
        );
    }

    /** Returns entity types in import-safe order (dependencies first). */
    public static List<CsvEntityType> importOrder() {
        return Arrays.asList(values());
    }

    @Override
    public String toString() {
        return name.toLowerCase(Locale.ROOT);
    }
}