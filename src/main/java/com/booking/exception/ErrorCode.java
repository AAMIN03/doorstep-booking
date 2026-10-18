package com.booking.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable machine-readable error codes returned in {@link com.booking.dto.ErrorResponse#code()}. Each code owns its HTTP
 * status. Codes with one message also own that message; codes with several messages take the text at the throw site.
 */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Request body is malformed or unreadable"),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST), NOT_FOUND(HttpStatus.NOT_FOUND),
    UNKNOWN_SERVICE(HttpStatus.NOT_FOUND, "Service not found"),
    UNKNOWN_PINCODE(HttpStatus.NOT_FOUND, "Pincode not served"),
    UNKNOWN_BOOKING(HttpStatus.NOT_FOUND, "Booking not found"),
    UNKNOWN_TECHNICIAN(HttpStatus.NOT_FOUND, "Technician not found"),
    INVALID_SLOT(HttpStatus.BAD_REQUEST, "Start time must align to 30-minute boundaries"),
    OUTSIDE_HORIZON(HttpStatus.BAD_REQUEST, "Booking must be between 2 hours and 7 days from now"),
    NO_TECHNICIAN_AVAILABLE(HttpStatus.CONFLICT, "No technician is available for the requested slot."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "Idempotency key reused with different payload"),
    LATE_CANCELLATION(HttpStatus.CONFLICT, "Cancellation must be at least 1 hour before start"),
    INVALID_CANCELLATION(HttpStatus.CONFLICT), CONFLICT(HttpStatus.CONFLICT),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Try again later."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");

    public static final String BOOKING_CONTENDED = "Booking temporarily contended, please retry";
    public static final String IDEMPOTENCY_RETRY = "Idempotency conflict, retry";
    public static final String ALREADY_CANCELLED = "Booking already cancelled";
    public static final String COMPLETED_CANNOT_CANCEL = "Completed booking cannot be cancelled";
    public static final String END_DATE_ORDER = "Parameter 'endDate': must be on or after startDate";

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status) {
        this(status, null);
    }

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
