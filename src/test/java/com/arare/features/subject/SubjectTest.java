package com.arare.features.subject;

import com.arare.common.enums.RoomType;
import com.arare.features.department.Department;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SubjectTest {

    @Test
    void prePersistValidateThrowsOnZeroChunkHours() throws Exception {
        Subject subject = Subject.builder()
            .name("Test Subject")
            .code("TS101")
            .department(Department.builder().name("CSE").code("CSE").build())
            .weeklyHours(4)
            .chunkHours(0)
            .roomTypeRequired(RoomType.LECTURE)
            .isLab(false)
            .requiresTeacher(true)
            .requiresRoom(true)
            .minGapBetweenSessions(0)
            .maxSessionsPerDay(1)
            .build();

        assertThrows(
            IllegalStateException.class,
            () -> invokeNormalize(subject)
        );
    }

    @Test
    void prePersistValidatePassesWithValidValues() throws Exception {
        Subject subject = Subject.builder()
            .name("Test Subject")
            .code("TS101")
            .department(Department.builder().name("CSE").code("CSE").build())
            .weeklyHours(4)
            .chunkHours(1)
            .roomTypeRequired(RoomType.LECTURE)
            .isLab(false)
            .requiresTeacher(true)
            .requiresRoom(true)
            .minGapBetweenSessions(0)
            .maxSessionsPerDay(1)
            .build();

        invokeNormalize(subject);
    }

    private void invokeNormalize(Subject subject) throws Exception {
        Method m = Subject.class.getDeclaredMethod("normalizeAndValidate");
        m.setAccessible(true);
        try {
            m.invoke(subject);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        }
    }
}
