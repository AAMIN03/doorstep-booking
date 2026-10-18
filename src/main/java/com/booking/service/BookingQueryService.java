package com.booking.service;

import com.booking.dto.BookingResponse;
import com.booking.dto.PageResponse;
import com.booking.entity.BookingEntity;
import com.booking.enums.BookingStatus;
import com.booking.exception.ApiException;
import com.booking.exception.ErrorCode;
import com.booking.repository.BookingRepository;
import com.booking.validation.PhoneNumberValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only booking search and pagination (by customer phone, technician schedule). */
@Service
public class BookingQueryService {

    private static final Logger log = LoggerFactory.getLogger(BookingQueryService.class);

    private final BookingRepository bookingRepository;
    private final BookingMapper mapper;
    private final TimeService timeService;

    public BookingQueryService(BookingRepository bookingRepository, BookingMapper mapper, TimeService timeService) {
        this.bookingRepository = bookingRepository;
        this.mapper = mapper;
        this.timeService = timeService;
    }

    /**
     * Lists bookings for a customer phone with optional status and IST date-range filters on {@code startTime}.
     *
     * @param startDate
     *            inclusive IST calendar start (optional)
     * @param endDate
     *            inclusive IST calendar end (optional); must be on or after {@code startDate} when both are set
     */
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> listByCustomerPhone(String customerPhoneHeader, String phone,
            BookingStatus status, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        PhoneNumberValidator.requireHeaderMatchesQueryPhone(customerPhoneHeader, phone);
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.END_DATE_ORDER);
        }
        Instant startInclusive = startDate != null ? timeService.istToUtc(startDate, LocalTime.MIN) : null;
        Instant endExclusive = endDate != null ? timeService.istToUtc(endDate.plusDays(1), LocalTime.MIN) : null;
        PageResponse<BookingResponse> page = loadPage(
                bookingRepository.findIdsByCustomerPhone(phone, status, startInclusive, endExclusive, pageable),
                pageable);
        log.debug("List bookings phone={} status={} total={}", phone, status, page.totalElements());
        return page;
    }

    /**
     * Paginated technician schedule. When {@code status} is omitted, every status is returned, including cancelled.
     * Date range defaults to the single required {@code anchorDate} unless {@code rangeStart} / {@code rangeEnd} widen
     * the IST window.
     */
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> technicianSchedule(Long technicianId, LocalDate anchorDate,
            LocalDate rangeStart, LocalDate rangeEnd, BookingStatus status, Pageable pageable) {
        LocalDate from = rangeStart != null ? rangeStart : anchorDate;
        LocalDate to = rangeEnd != null ? rangeEnd : anchorDate;
        if (to.isBefore(from)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.END_DATE_ORDER);
        }
        Instant dayStart = timeService.istToUtc(from, LocalTime.MIN);
        Instant dayEnd = timeService.istToUtc(to.plusDays(1), LocalTime.MIN);
        return loadPage(
                bookingRepository.findIdsForTechnicianSchedule(technicianId, status, dayStart, dayEnd, pageable),
                pageable);
    }

    private PageResponse<BookingResponse> loadPage(Page<Long> idPage, Pageable pageable) {
        List<Long> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return PageResponse.from(new PageImpl<>(List.of(), pageable, idPage.getTotalElements()));
        }
        List<BookingEntity> loaded = bookingRepository.findDetailedByIdIn(ids);
        Map<Long, BookingEntity> byId = new LinkedHashMap<>();
        for (BookingEntity b : loaded) {
            byId.put(b.getId(), b);
        }
        List<BookingEntity> ordered = new ArrayList<>();
        for (Long id : ids) {
            BookingEntity entity = byId.get(id);
            if (entity != null) {
                ordered.add(entity);
            }
        }
        Page<BookingEntity> page = new PageImpl<>(ordered, pageable, idPage.getTotalElements());
        return PageResponse.from(page, mapper::toResponse);
    }
}
