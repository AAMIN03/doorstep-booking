package com.booking.exception;

/**
 * Stable machine-readable error codes returned in {@link com.booking.dto.ErrorResponse#code()}.
 * Binding failures use {@link #VALIDATION_ERROR}, {@link #INVALID_PARAMETER}, or {@link #MALFORMED_REQUEST};
 * domain failures use the {@code UNKNOWN_*}, slot/horizon, cancellation, and conflict codes below.
 */
public enum ErrorCode {
    VALIDATION_ERROR,
    MALFORMED_REQUEST,
    INVALID_PARAMETER,
    NOT_FOUND,
    UNKNOWN_SERVICE,
    UNKNOWN_PINCODE,
    UNKNOWN_BOOKING,
    UNKNOWN_TECHNICIAN,
    INVALID_SLOT,
    OUTSIDE_HORIZON,
    NO_TECHNICIAN_AVAILABLE,
    IDEMPOTENCY_CONFLICT,
    LATE_CANCELLATION,
    INVALID_CANCELLATION,
    CONFLICT,
    RATE_LIMIT_EXCEEDED,
    INTERNAL_ERROR
}
