package com.booking.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.booking.dto.ErrorResponse;
import com.booking.service.TimeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** Unit tests for {@link GlobalExceptionHandler} status, {@link ErrorCode}, and message conventions. */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private TimeService timeService;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(timeService);
        when(timeService.toIst(any(Instant.class)))
                .thenReturn(ZonedDateTime.of(2026, 10, 18, 14, 0, 0, 0, ZoneId.of("Asia/Kolkata")));
    }

    @Test
    void missingServletParameterUsesInvalidParameterCode() {
        HttpServletRequest req = request("/availability");
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("serviceId", "Long");

        ResponseEntity<ErrorResponse> response = handler.handleMissingParameter(ex, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertErrorBody(response.getBody(), 400, "BAD_REQUEST", "INVALID_PARAMETER",
                "Parameter 'serviceId': is required", "/availability");
    }

    @Test
    void typeMismatchUsesInvalidParameterCode() {
        HttpServletRequest req = request("/availability");
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException("bad", LocalDate.class, "date",
                null, null);

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertErrorBody(response.getBody(), 400, "BAD_REQUEST", "INVALID_PARAMETER",
                "Parameter 'date': invalid value 'bad'", "/availability");
    }

    @Test
    void unreadableBodyUsesMalformedRequestCode() {
        HttpServletRequest req = request("/bookings");
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad json", null, null);

        ResponseEntity<ErrorResponse> response = handler.handleUnreadable(ex, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertErrorBody(response.getBody(), 400, "BAD_REQUEST", "MALFORMED_REQUEST",
                "Request body is malformed or unreadable", "/bookings");
    }

    @Test
    void beanValidationUsesFieldMessageConvention() throws Exception {
        HttpServletRequest req = request("/bookings");
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "customerPhone", "must be a valid Indian mobile number"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter(), binding);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertErrorBody(response.getBody(), 400, "BAD_REQUEST", "VALIDATION_ERROR",
                "Field 'customerPhone': must be a valid Indian mobile number", "/bookings");
    }

    @Test
    void missingHeaderUsesHeaderMessageConvention() {
        HttpServletRequest req = request("/bookings");
        MissingRequestHeaderException ex = new MissingRequestHeaderException("Idempotency-Key", methodParameter());

        ResponseEntity<ErrorResponse> response = handler.handleMissingHeader(ex, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertErrorBody(response.getBody(), 400, "BAD_REQUEST", "VALIDATION_ERROR",
                "Header 'Idempotency-Key' is required", "/bookings");
    }

    @Test
    void apiExceptionPreservesStatusAndCode() {
        HttpServletRequest req = request("/bookings/1");
        ApiException ex = new ApiException(HttpStatus.NOT_FOUND, ErrorCode.UNKNOWN_BOOKING, "Booking not found");

        ResponseEntity<ErrorResponse> response = handler.handleApi(ex, req);

        assertErrorBody(response.getBody(), 404, "NOT_FOUND", "UNKNOWN_BOOKING", "Booking not found", "/bookings/1");
    }

    @Test
    void lockingFailureMapsToConflict() {
        HttpServletRequest req = request("/bookings");
        PessimisticLockingFailureException ex = new PessimisticLockingFailureException("lock", null);

        ResponseEntity<ErrorResponse> response = handler.handleLocking(ex, req);

        assertErrorBody(response.getBody(), 409, "CONFLICT", "CONFLICT", "Booking temporarily contended, please retry",
                "/bookings");
    }

    @Test
    void unhandledExceptionMapsToInternalError() {
        HttpServletRequest req = request("/bookings");

        ResponseEntity<ErrorResponse> response = handler.handleOther(new RuntimeException("boom"), req);

        assertErrorBody(response.getBody(), 500, "INTERNAL_SERVER_ERROR", "INTERNAL_ERROR", "Unexpected error",
                "/bookings");
    }

    @Test
    void constraintViolationUsesParameterMessageConvention() {
        HttpServletRequest req = request("/availability");
        @SuppressWarnings("unchecked")
        ConstraintViolation<String> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("pincode");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must match pattern");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertErrorBody(response.getBody(), 400, "BAD_REQUEST", "INVALID_PARAMETER",
                "Parameter 'pincode': must match pattern", "/availability");
    }

    @Test
    void clientFailureIsLoggedAtWarnWithStackTrace() {
        ListAppender<ILoggingEvent> events = captureLogs();
        try {
            handler.handleApi(new ApiException(HttpStatus.NOT_FOUND, ErrorCode.UNKNOWN_BOOKING, "Booking not found"),
                    request("/bookings/1"));
            assertStackTrace(events, Level.WARN);
        } finally {
            detach(events);
        }
    }

    @Test
    void unexpectedFailureIsLoggedAtErrorWithStackTrace() {
        ListAppender<ILoggingEvent> events = captureLogs();
        try {
            handler.handleOther(new RuntimeException("boom"), request("/bookings"));
            assertStackTrace(events, Level.ERROR);
        } finally {
            detach(events);
        }
    }

    private static ListAppender<ILoggingEvent> captureLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        return appender;
    }

    private static void detach(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logger.detachAppender(appender);
    }

    private static void assertStackTrace(ListAppender<ILoggingEvent> events, Level level) {
        assertThat(events.list).isNotEmpty();
        ILoggingEvent event = events.list.get(events.list.size() - 1);
        assertThat(event.getLevel()).isEqualTo(level);
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getThrowableProxy().getStackTraceElementProxyArray()).isNotEmpty();
    }

    private static MethodParameter methodParameter() {
        try {
            Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("sample", String.class);
            return new MethodParameter(method, 0);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @SuppressWarnings("unused")
    private static void sample(String body) {
    }

    private static HttpServletRequest request(String uri) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn(uri);
        return req;
    }

    private static void assertErrorBody(ErrorResponse body, int status, String httpError, String code, String message,
            String path) {
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo(code);
        assertThat(body.message()).isEqualTo(message);
        assertThat(body.path()).isEqualTo(path);
        assertThat(body.status()).isEqualTo(status);
        assertThat(body.error()).isEqualTo(httpError);
        assertThat(body.timestamp()).isNotNull();
    }
}
