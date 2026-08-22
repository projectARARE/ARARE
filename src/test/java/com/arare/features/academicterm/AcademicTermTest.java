package com.arare.features.academicterm;

import com.arare.common.enums.AcademicTermStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AcademicTermTest {

    @Test
    void prePersistValidateThrowsOnNullDates() throws Exception {
        AcademicTerm term = AcademicTerm.builder()
            .name("Semester 1")
            .status(AcademicTermStatus.UPCOMING)
            .build();

        assertThrows(
            IllegalStateException.class,
            () -> invokeNormalize(term)
        );
    }

    @Test
    void prePersistValidateThrowsWhenEndDateBeforeStartDate() throws Exception {
        AcademicTerm term = AcademicTerm.builder()
            .name("Semester 1")
            .startDate(LocalDate.of(2025, 6, 1))
            .endDate(LocalDate.of(2025, 5, 1))
            .status(AcademicTermStatus.UPCOMING)
            .build();

        assertThrows(
            IllegalStateException.class,
            () -> invokeNormalize(term)
        );
    }

    @Test
    void prePersistValidatePassesWithValidDates() throws Exception {
        AcademicTerm term = AcademicTerm.builder()
            .name("Semester 1")
            .startDate(LocalDate.of(2025, 1, 1))
            .endDate(LocalDate.of(2025, 6, 1))
            .status(AcademicTermStatus.UPCOMING)
            .build();

        invokeNormalize(term);
    }

    private void invokeNormalize(AcademicTerm term) throws Exception {
        Method m = AcademicTerm.class.getDeclaredMethod("normalizeAndValidate");
        m.setAccessible(true);
        try {
            m.invoke(term);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        }
    }
}
