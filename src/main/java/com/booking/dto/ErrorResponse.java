package com.booking.dto;

import java.time.ZonedDateTime;

/** Canonical API error body produced by {@link com.booking.exception.GlobalExceptionHandler}. */
public record ErrorResponse(
        ZonedDateTime timestamp,
        int status,
        String error,
        String code,
        String message,
        String path) {
}
