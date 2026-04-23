package com.devtools.ui.relay;

import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
class RelayErrorHandler {

    @ExceptionHandler(RelayAuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    Map<String, String> handleUnauthorized(RelayAuthException exception) {
        return Map.of("error", "unauthorized", "message", exception.getMessage());
    }

    @ExceptionHandler(PlanRequiredException.class)
    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    Map<String, String> handlePlanRequired(PlanRequiredException exception) {
        return Map.of("error", "plan_required", "message", exception.getMessage());
    }

    @ExceptionHandler(QuotaExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    Map<String, String> handleQuotaExceeded(QuotaExceededException exception) {
        return Map.of("error", "quota_exceeded", "message", exception.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, String> handleNotFound(NoSuchElementException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, String> handleNoHandlerFound(NoHandlerFoundException exception) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", "404",
                "error", "Not Found",
                "path", exception.getRequestURL()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> handleBadRequest(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    Map<String, String> handleForbidden(IllegalStateException exception) {
        return Map.of("error", exception.getMessage());
    }
}
