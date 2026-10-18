package com.booking.service;

import com.booking.dto.BookingResponse;
import com.booking.dto.CreateBookingRequest;
import com.booking.entity.BookingEntity;
import com.booking.enums.BookingStatus;
import com.booking.exception.ApiException;
import com.booking.exception.ErrorCode;
import com.booking.repository.BookingRepository;
import com.booking.validation.PhoneNumberValidator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final int IDEMPOTENCY_WAIT_ATTEMPTS = 40;
    private static final long IDEMPOTENCY_WAIT_MILLIS = 25;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final BookingRepository bookingRepository;
    private final BookingTxService bookingTxService;
    private final IdempotencyService idempotencyService;
    private final SchedulingRules rules;
    private final BookingMapper mapper;

    public BookingService(BookingRepository bookingRepository, BookingTxService bookingTxService,
            IdempotencyService idempotencyService, SchedulingRules rules, BookingMapper mapper) {
        this.bookingRepository = bookingRepository;
        this.bookingTxService = bookingTxService;
        this.idempotencyService = idempotencyService;
        this.rules = rules;
        this.mapper = mapper;
    }

    /**
     * Returns a booking only when {@code customerPhone} matches the stored {@code customerPhone} (same 404 as unknown
     * id when it does not).
     */
    @Transactional(readOnly = true)
    public BookingResponse getBooking(Long id, String customerPhone) {
        PhoneNumberValidator.requireValidCustomerHeader(customerPhone);
        BookingResponse response = mapper.toResponse(requireBookingOwnedBy(id, customerPhone, false));
        log.debug("Get booking id={} status={}", response.id(), response.status());
        return response;
    }

    public BookingResult create(String idempotencyKey, CreateBookingRequest request) {
        String hash = hash(request);
        BookingResult cached = idempotencyService.findResult(idempotencyKey, hash);
        if (cached != null) {
            log.debug("Idempotent replay key={} bookingId={}", idempotencyKey, cached.response().id());
            return cached;
        }
        try {
            return bookingTxService.create(idempotencyKey, request, hash);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Idempotency key {} already inserted, waiting for the original booking", idempotencyKey);
            return waitForIdempotentResult(idempotencyKey, hash);
        } catch (PessimisticLockingFailureException ex) {
            throw new ApiException(ErrorCode.CONFLICT, ErrorCode.BOOKING_CONTENDED, ex);
        }
    }

    private BookingResult waitForIdempotentResult(String key, String hash) {
        for (int i = 0; i < IDEMPOTENCY_WAIT_ATTEMPTS; i++) {
            BookingResult retry = idempotencyService.findResult(key, hash);
            if (retry != null) {
                return retry;
            }
            try {
                Thread.sleep(IDEMPOTENCY_WAIT_MILLIS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for idempotent booking key={}", key, ex);
                break;
            }
        }
        throw new ApiException(ErrorCode.CONFLICT, ErrorCode.IDEMPOTENCY_RETRY);
    }

    /**
     * Cancels a booking under pessimistic lock. Evaluates status and {@link SchedulingRules#cancelTooLate} in order:
     * already {@link BookingStatus#CANCELLED}, {@link BookingStatus#COMPLETED}, then within one hour of start.
     *
     * @throws com.booking.exception.ApiException
     *             404 {@link ErrorCode#UNKNOWN_BOOKING}; 409 {@link ErrorCode#INVALID_CANCELLATION} or
     *             {@link ErrorCode#LATE_CANCELLATION}
     */
    @Transactional
    public BookingResponse cancel(Long bookingId, String customerPhone) {
        PhoneNumberValidator.requireValidCustomerHeader(customerPhone);
        BookingEntity booking = requireBookingOwnedBy(bookingId, customerPhone, true);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ApiException(ErrorCode.INVALID_CANCELLATION, ErrorCode.ALREADY_CANCELLED);
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new ApiException(ErrorCode.INVALID_CANCELLATION, ErrorCode.COMPLETED_CANNOT_CANCEL);
        }
        if (rules.cancelTooLate(booking.getStartTime())) {
            throw new ApiException(ErrorCode.LATE_CANCELLATION);
        }
        booking.setStatus(BookingStatus.CANCELLED);
        log.debug("Cancelled booking id={}", bookingId);
        return mapper.toResponse(booking);
    }

    private BookingEntity requireBookingOwnedBy(Long id, String customerPhone, boolean forUpdate) {
        BookingEntity booking = (forUpdate ? bookingRepository.findDetailedByIdForUpdate(id)
                : bookingRepository.findDetailedById(id))
                        .orElseThrow(() -> new ApiException(ErrorCode.UNKNOWN_BOOKING));
        if (!booking.getCustomerPhone().equals(customerPhone)) {
            throw new ApiException(ErrorCode.UNKNOWN_BOOKING);
        }
        return booking;
    }

    static String hash(CreateBookingRequest request) {
        String raw = request.customerPhone() + "|" + request.serviceId() + "|" + request.pincode() + "|"
                + request.startTime();
        try {
            byte[] digest = MessageDigest.getInstance(HASH_ALGORITHM).digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record BookingResult(BookingResponse response, boolean created) {
    }
}
