package com.arare.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void genericExceptionDoesNotExposeInternalMessage() {
        Exception ex = new Exception("Caused by: java.lang.NullPointerException at com.arare.internal.secret(ClassLoader)");

        ProblemDetail detail = handler.handleGeneric(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), detail.getStatus());
        assertFalse(detail.getDetail().contains("NullPointerException"));
        assertFalse(detail.getDetail().contains("com.arare.internal"));
        assertFalse(detail.getDetail().contains("ClassLoader"));
        assertTrue(detail.getDetail().contains("unexpected error"));
    }

    @Test
    void unreadableBodyDoesNotExposeInternalMessage() {
        HttpMessageNotReadableException ex =
            new HttpMessageNotReadableException(
                "JSON parse error: Unexpected token at com.fasterxml.jackson.core.JsonParser.internalStackTrace",
                new MockHttpInputMessage("{}".getBytes(StandardCharsets.UTF_8)));

        ProblemDetail detail = handler.handleUnreadableBody(ex);

        assertFalse(detail.getDetail().contains("JsonParser"));
        assertFalse(detail.getDetail().contains("internalStackTrace"));
    }

    @Test
    void methodNotSupportedReturns405WithSafeMessage() {
        HttpRequestMethodNotSupportedException ex =
            new HttpRequestMethodNotSupportedException("PATCH", java.util.List.of("GET", "POST"));

        ProblemDetail detail = handler.handleMethodNotSupported(ex);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED.value(), detail.getStatus());
        assertFalse(detail.getDetail().contains("PATCH"));
    }

    @Test
    void infeasibleScheduleReturns422() {
        InfeasibleScheduleException ex =
            new InfeasibleScheduleException("Schedule request is infeasible: no qualified teacher");

        ProblemDetail detail = handler.handleInfeasible(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), detail.getStatus());
        assertEquals("/errors/infeasible", detail.getType().toString());
    }

    @Test
    void illegalStateExceptionReturns500AndNotInfeasible() {
        IllegalStateException ex = new IllegalStateException("unexpected internal state");

        ProblemDetail detail = handler.handleIllegalState(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), detail.getStatus());
        assertEquals("/errors/internal", detail.getType().toString());
    }
}
