package com.booking.exception;

import org.springframework.http.HttpStatus;

/** Domain failure mapped to HTTP status and {@link ErrorCode} by {@link GlobalExceptionHandler}. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;

    public ApiException(ErrorCode code) {
        this(code.status(), code, code.message());
    }

    public ApiException(ErrorCode code, String message) {
        this(code.status(), code, message);
    }

    public ApiException(ErrorCode code, String message, Throwable cause) {
        this(code.status(), code, message, cause);
    }

    public ApiException(HttpStatus status, ErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public ApiException(HttpStatus status, ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }
}
