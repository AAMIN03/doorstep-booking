package com.booking.exception;

import com.booking.dto.ErrorResponse;
import com.booking.service.TimeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;

/**
 * Maps exceptions to a single {@link com.booking.dto.ErrorResponse} JSON shape. Client binding errors return 400 with
 * {@link ErrorCode#VALIDATION_ERROR}, {@link ErrorCode#INVALID_PARAMETER}, or {@link ErrorCode#MALFORMED_REQUEST} and
 * standardized {@code message} patterns ({@code Field '…'}, {@code Parameter '…'}, {@code Header '…'}).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final TimeService timeService;

    public GlobalExceptionHandler(TimeService timeService) {
        this.timeService = timeService;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest req) {
        logFailure(ex, ex.getStatus());
        return body(ex.getStatus(), ex.getCode(), ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String msg = ex.getBindingResult().getFieldErrors().stream().findFirst()
                .map(f -> fieldMessage(f.getField(), f.getDefaultMessage())).orElse("Request body failed validation");
        logFailure(ex, ErrorCode.VALIDATION_ERROR.status());
        return body(ErrorCode.VALIDATION_ERROR.status(), ErrorCode.VALIDATION_ERROR, msg, req.getRequestURI());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest req) {
        logFailure(ex, ErrorCode.VALIDATION_ERROR.status());
        return body(ErrorCode.VALIDATION_ERROR.status(), ErrorCode.VALIDATION_ERROR, headerRequired(ex.getHeaderName()),
                req.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        logFailure(ex, ErrorCode.MALFORMED_REQUEST.status());
        return body(ErrorCode.MALFORMED_REQUEST.status(), ErrorCode.MALFORMED_REQUEST,
                ErrorCode.MALFORMED_REQUEST.message(), req.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
            HttpServletRequest req) {
        String detail = ex.getValue() != null ? "invalid value '" + ex.getValue() + "'" : "invalid value";
        logFailure(ex, ErrorCode.INVALID_PARAMETER.status());
        return body(ErrorCode.INVALID_PARAMETER.status(), ErrorCode.INVALID_PARAMETER,
                parameterMessage(ex.getName(), detail), req.getRequestURI());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
            HttpServletRequest req) {
        logFailure(ex, ErrorCode.INVALID_PARAMETER.status());
        return body(ErrorCode.INVALID_PARAMETER.status(), ErrorCode.INVALID_PARAMETER,
                parameterMessage(ex.getParameterName(), "is required"), req.getRequestURI());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
            HttpServletRequest req) {
        String msg = ex.getConstraintViolations().stream().findFirst().map(GlobalExceptionHandler::constraintMessage)
                .orElse("Request parameter failed validation");
        logFailure(ex, ErrorCode.INVALID_PARAMETER.status());
        return body(ErrorCode.INVALID_PARAMETER.status(), ErrorCode.INVALID_PARAMETER, msg, req.getRequestURI());
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleLocking(PessimisticLockingFailureException ex, HttpServletRequest req) {
        logFailure(ex, ErrorCode.CONFLICT.status());
        return body(ErrorCode.CONFLICT.status(), ErrorCode.CONFLICT, ErrorCode.BOOKING_CONTENDED, req.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception ex, HttpServletRequest req) {
        logFailure(ex, ErrorCode.INTERNAL_ERROR.status());
        return body(ErrorCode.INTERNAL_ERROR.status(), ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.message(),
                req.getRequestURI());
    }

    private static String fieldMessage(String field, String detail) {
        return "Field '" + field + "': " + (detail != null ? detail : "is invalid");
    }

    private static String parameterMessage(String name, String detail) {
        return "Parameter '" + name + "': " + detail;
    }

    private static String headerRequired(String name) {
        return "Header '" + name + "' is required";
    }

    private static String constraintMessage(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "request";
        return parameterMessage(path, violation.getMessage());
    }

    /** WARN for client failures and ERROR for 5xx. The throwable is last so the JSON log includes stack_trace. */
    private static void logFailure(Exception ex, HttpStatus status) {
        if (status.is5xxServerError()) {
            log.error("Request failed with status {}", status.value(), ex);
        } else {
            log.warn("Request failed with status {}", status.value(), ex);
        }
    }

    private ResponseEntity<ErrorResponse> body(HttpStatus status, ErrorCode code, String message, String path) {
        ZonedDateTime ts = timeService.toIst(java.time.Instant.now());
        return ResponseEntity.status(status)
                .body(new ErrorResponse(ts, status.value(), status.name(), code.name(), message, path));
    }
}
