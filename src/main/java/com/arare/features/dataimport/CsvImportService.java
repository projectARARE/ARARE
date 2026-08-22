package com.arare.features.dataimport;

import com.arare.features.batch.BatchRepository;
import com.arare.features.building.BuildingRepository;
import com.arare.features.department.DepartmentRepository;
import com.arare.features.room.RoomRepository;
import com.arare.features.subject.SubjectRepository;
import com.arare.features.teacher.TeacherRepository;
import com.arare.features.timeslot.TimeslotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

/**
 * Single-entity CSV importer used by the {@code POST /import/csv/{entityType}}
 * endpoint. Imports one entity type at a time in the flat, Excel-friendly
 * format — relationships may be embedded in the same file via token columns
 * (e.g. {@code subjectCodes}, {@code availableTimeslots}).
 *
 * <p>Before import, natural-key dependencies are validated: an entity type
 * can only be imported once the entity kinds it references already exist in
 * the database (see {@link CsvEntityType#getDependencies()}). This enforces
 * the documented import order, e.g. subjects must exist before teachers.
 *
 * <p>Partial-update semantics: only the rows present in the file are
 * touched; nothing else is modified.
 */
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private final TimeslotRepository timeslotRepository;
    private final BuildingRepository buildingRepository;
    private final DepartmentRepository departmentRepository;
    private final RoomRepository roomRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final BatchRepository batchRepository;
    private final CsvEntityUpserter upserter;

    @Transactional
    public CsvImportResponse importCsv(String entityTypeRaw, String csvContent) {
        return importCsv(entityTypeRaw, csvContent, false);
    }

    @Transactional
    public CsvImportResponse importCsv(String entityTypeRaw, String csvContent, boolean dryRun) {
        CsvEntityType entityType = CsvEntityType.fromName(entityTypeRaw);
        validateDependencies(entityType);

        ImportContext context = new ImportContext(
            timeslotRepository, buildingRepository, departmentRepository,
            roomRepository, subjectRepository, teacherRepository, batchRepository);
        context.loadFromDatabase();

        List<Map<String, String>> rows = CsvUtils.parse(csvContent);
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new java.util.ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 2;
            try {
                if (upserter.upsert(entityType, rows.get(i), rowNumber, context)) {
                    created++;
                } else {
                    updated++;
                }
            } catch (Exception ex) {
                skipped++;
                errors.add("Row " + rowNumber + ": " + ex.getMessage());
            }
        }

        markRollbackIfDryRun(dryRun);
        return new CsvImportResponse(entityType.getName(), created, updated, skipped, errors, dryRun);
    }

    /**
     * In dry-run mode nothing is written to the database: the enclosing
     * transaction is marked rollback-only while the response still reports
     * the changes that would have been applied.
     */
    private void markRollbackIfDryRun(boolean dryRun) {
        if (dryRun && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }

    /** Refuses to import an entity type whose dependencies are not yet present. */
    private void validateDependencies(CsvEntityType entityType) {
        ImportContext context = new ImportContext(
            timeslotRepository, buildingRepository, departmentRepository,
            roomRepository, subjectRepository, teacherRepository, batchRepository);
        context.loadFromDatabase();

        List<String> missing = new java.util.ArrayList<>();
        for (CsvEntityType dependency : entityType.getDependencies()) {
            if (!context.containsAny(dependency)) {
                missing.add(dependency.getDisplayName());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                entityType.getDisplayName() + " cannot be imported before "
                    + String.join(", ", missing)
                    + ". Import those first or use the relational ZIP import which processes files in order."
            );
        }
    }
}