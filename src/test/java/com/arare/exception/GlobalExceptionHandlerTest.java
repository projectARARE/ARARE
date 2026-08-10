package com.arare.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleOptimisticLockReturns409() {
        ObjectOptimisticLockingFailureException ex =
            new ObjectOptimisticLockingFailureException("Entity", 1L, "stale version", null);

        ProblemDetail detail = handler.handleOptimisticLock(ex);

        assertEquals(HttpStatus.CONFLICT.value(), detail.getStatus());
    }

    @Test
    void handleUnreadableBodyReturns400() {
        HttpMessageNotReadableException ex =
            new HttpMessageNotReadableException("malformed JSON", new MockHttpInputMessage("{}".getBytes(StandardCharsets.UTF_8)));

        ProblemDetail detail = handler.handleUnreadableBody(ex);

        assertEquals(HttpStatus.BAD_REQUEST.value(), detail.getStatus());
    }
}
