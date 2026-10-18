package com.booking.controller;

import com.booking.dto.AvailabilityResponse;
import com.booking.dto.BookingResponse;
import com.booking.dto.CreateBookingRequest;
import com.booking.dto.PageResponse;
import com.booking.dto.ScheduleResponse;
import com.booking.enums.BookingStatus;
import com.booking.service.AvailabilityService;
import com.booking.service.BookingQueryService;
import com.booking.service.BookingService;
import com.booking.service.TechnicianScheduleService;
import com.booking.web.ApiPaths;
import com.booking.web.CustomerIdentity;
import com.booking.web.Idempotency;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST API: availability, bookings (list by phone, create, get, cancel), technician schedule. */
@RestController
@RequestMapping
public class BookingController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final String SORT_BY_START_TIME = "startTime";

    private final AvailabilityService availabilityService;
    private final BookingService bookingService;
    private final BookingQueryService bookingQueryService;
    private final TechnicianScheduleService scheduleService;

    public BookingController(AvailabilityService availabilityService, BookingService bookingService,
            BookingQueryService bookingQueryService, TechnicianScheduleService scheduleService) {
        this.availabilityService = availabilityService;
        this.bookingService = bookingService;
        this.bookingQueryService = bookingQueryService;
        this.scheduleService = scheduleService;
    }

    @GetMapping(ApiPaths.AVAILABILITY)
    public AvailabilityResponse availability(@RequestParam Long serviceId, @RequestParam String pincode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return availabilityService.getAvailability(serviceId, pincode, date);
    }

    /**
     * Lists bookings for a customer. {@code phone} is required; optional {@code status}, {@code startDate},
     * {@code endDate} (IST, inclusive) filter on booking start. Pagination: {@code page}, {@code size}, {@code sort}
     * (e.g. {@code sort=startTime,desc}).
     */
    @GetMapping(ApiPaths.BOOKINGS)
    public PageResponse<BookingResponse> listByPhone(@RequestHeader(CustomerIdentity.HEADER) String customerPhone,
            @RequestParam String phone, @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = SORT_BY_START_TIME, direction = Sort.Direction.DESC) Pageable pageable) {
        return bookingQueryService.listByCustomerPhone(customerPhone, phone, status, startDate, endDate, pageable);
    }

    @PostMapping(ApiPaths.BOOKINGS)
    public ResponseEntity<BookingResponse> create(@RequestHeader(Idempotency.HEADER) String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request) {
        BookingService.BookingResult result = bookingService.create(idempotencyKey, request);
        if (result.created()) {
            return ResponseEntity.created(URI.create(ApiPaths.bookingLocation(result.response().id())))
                    .body(result.response());
        }
        return ResponseEntity.ok(result.response());
    }

    @GetMapping(ApiPaths.BOOKING)
    public BookingResponse get(@PathVariable Long id, @RequestHeader(CustomerIdentity.HEADER) String customerPhone) {
        return bookingService.getBooking(id, customerPhone);
    }

    /**
     * Cancels a confirmed booking. Returns 404 {@code UNKNOWN_BOOKING}, or 409 {@code INVALID_CANCELLATION} /
     * {@code LATE_CANCELLATION} per {@link com.booking.service.BookingService#cancel(Long)}.
     */
    @PostMapping(ApiPaths.CANCEL)
    public BookingResponse cancel(@PathVariable Long id, @RequestHeader(CustomerIdentity.HEADER) String customerPhone) {
        return bookingService.cancel(id, customerPhone);
    }

    /**
     * Paginated technician schedule for {@code date} (required). Optional {@code startDate}/{@code endDate} widen the
     * IST window; optional {@code status} filters (default returns every status). Pagination: {@code page},
     * {@code size}, {@code sort} (default {@code startTime,asc}).
     */
    @GetMapping(ApiPaths.TECHNICIAN_SCHEDULE)
    public ScheduleResponse schedule(@PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = SORT_BY_START_TIME, direction = Sort.Direction.ASC) Pageable pageable) {
        return scheduleService.schedule(id, date, startDate, endDate, status, pageable);
    }
}
