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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingTxService bookingTxService;
    private final IdempotencyService idempotencyService;
    private final SchedulingRules rules;
    private final BookingMapper mapper;

    public BookingService(
            BookingRepository bookingRepository,
            BookingTxService bookingTxService,
            IdempotencyService idempotencyService,
            SchedulingRules rules,
            BookingMapper mapper) {
        this.bookingRepository = bookingRepository;
        this.bookingTxService = bookingTxService;
        this.idempotencyService = idempotencyService;
        this.rules = rules;
        this.mapper = mapper;
    }

    /**
     * Returns a booking only when {@code customerPhone} matches the stored {@code customerPhone}
     * (same 404 as unknown id when it does not).
     */
    @Transactional(readOnly = true)
    public BookingResponse getBooking(Long id, String customerPhone) {
        PhoneNumberValidator.requireValidCustomerHeader(customerPhone);
        return mapper.toResponse(requireBookingOwnedBy(id, customerPhone, false));
    }

    public BookingResult create(String idempotencyKey, CreateBookingRequest request) {
        String hash = hash(request);
        BookingResult cached = idempotencyService.findResult(idempotencyKey, hash);
        if (cached != null) {
            return cached;
        }
        try {
            return bookingTxService.create(idempotencyKey, request, hash);
        } catch (DataIntegrityViolationException ex) {
            return waitForIdempotentResult(idempotencyKey, hash);
        } catch (PessimisticLockingFailureException ex) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ErrorCode.CONFLICT, "Booking temporarily contended, please retry");
        }
    }

    private BookingResult waitForIdempotentResult(String key, String hash) {
        for (int i = 0; i < 40; i++) {
            BookingResult retry = idempotencyService.findResult(key, hash);
            if (retry != null) {
                return retry;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "Idempotency conflict, retry");
    }

    /**
     * Cancels a booking under pessimistic lock. Evaluates status and {@link SchedulingRules#cancelTooLate}
     * in order: already {@link BookingStatus#CANCELLED}, {@link BookingStatus#COMPLETED}, then within one hour of start.
     *
     * @throws com.booking.exception.ApiException 404 {@link ErrorCode#UNKNOWN_BOOKING}; 409
     *     {@link ErrorCode#INVALID_CANCELLATION} or {@link ErrorCode#LATE_CANCELLATION}
     */
    @Transactional
    public BookingResponse cancel(Long bookingId, String customerPhone) {
        PhoneNumberValidator.requireValidCustomerHeader(customerPhone);
        BookingEntity booking = requireBookingOwnedBy(bookingId, customerPhone, true);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_CANCELLATION, "Booking already cancelled");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_CANCELLATION, "Completed booking cannot be cancelled");
        }
        if (rules.cancelTooLate(booking.getStartTime())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.LATE_CANCELLATION, "Cancellation must be at least 1 hour before start");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        return mapper.toResponse(booking);
    }

    private BookingEntity requireBookingOwnedBy(Long id, String customerPhone, boolean forUpdate) {
        BookingEntity booking = (forUpdate
                        ? bookingRepository.findDetailedByIdForUpdate(id)
                        : bookingRepository.findDetailedById(id))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.UNKNOWN_BOOKING, "Booking not found"));
        if (!booking.getCustomerPhone().equals(customerPhone)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.UNKNOWN_BOOKING, "Booking not found");
        }
        return booking;
    }

    static String hash(CreateBookingRequest request) {
        String raw = request.customerPhone() + "|" + request.serviceId() + "|" + request.pincode() + "|" + request.startTime();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record BookingResult(BookingResponse response, boolean created) {}
}
