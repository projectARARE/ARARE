package com.arare.features.dataimport;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvEntityTypeTest {

    @Test
    void importOrderSatisfiesAllDependencies() {
        List<CsvEntityType> order = CsvEntityType.importOrder();
        Set<CsvEntityType> seen = java.util.HashSet.newHashSet(order.size());

        for (CsvEntityType type : order) {
            for (CsvEntityType dependency : type.getDependencies()) {
                assertTrue(seen.contains(dependency),
                    type + " must come after " + dependency);
            }
            seen.add(type);
        }
    }

    @Test
    void teachersDependOnSubjectsAndDepartments() {
        assertTrue(CsvEntityType.TEACHERS.getDependencies().contains(CsvEntityType.SUBJECTS));
        assertTrue(CsvEntityType.TEACHERS.getDependencies().contains(CsvEntityType.DEPARTMENTS));
    }

    @Test
    void roomsDependOnBuildings() {
        assertTrue(CsvEntityType.ROOMS.getDependencies().contains(CsvEntityType.BUILDINGS));
    }

    @Test
    void fromNameIsCaseInsensitive() {
        assertEquals(CsvEntityType.TIMESLOTS, CsvEntityType.fromName("Timeslots"));
        assertEquals(CsvEntityType.BUILDINGS, CsvEntityType.fromName("BUILDINGS"));
    }

    @Test
    void fromNameRejectsUnknownTypes() {
        assertThrows(IllegalArgumentException.class, () -> CsvEntityType.fromName("gadgets"));
    }
}